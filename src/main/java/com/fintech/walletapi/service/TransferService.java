package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.TransferRecordDTO;
import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.SupportedCurrency;
import com.fintech.walletapi.entity.TransferRecord;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.TransferRecordRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The transfer engine: moves money between wallets and keeps the ledger.
 *
 * <p>A transfer is three database writes — debit the source, credit the
 * destination, insert a ledger row. {@link Transactional} makes them atomic:
 * if anything fails after the first write, Spring rolls all of them back.</p>
 *
 * <p>When the two wallets hold different currencies (e.g. a PHP wallet paying
 * into a USD forex wallet) the amount is converted with
 * {@link ExchangeRateService} inside the same transaction.</p>
 */
@Service
@Transactional
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final ExchangeRateService exchangeRateService;

    /**
     * Creates the service.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param accountRepository        wallet repository
     * @param transferRecordRepository ledger repository
     * @param exchangeRateService      forex engine (only used for cross-currency transfers)
     */
    public TransferService(AccountRepository accountRepository,
                           TransferRecordRepository transferRecordRepository,
                           ExchangeRateService exchangeRateService) {
        this.accountRepository = accountRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.exchangeRateService = exchangeRateService;
    }

    /**
     * Sends money from one wallet to another, converting currency if needed.
     *
     * <p><b>Pre-condition:</b> {@code authenticatedUsername} comes from the JWT and owns
     * {@code fromId}; {@code amount} &gt; 0 with at most 2 decimals, in the source currency;
     * the source balance is at least {@code amount}.</p>
     * <p><b>Post-condition:</b> on success the source is debited {@code amount}, the destination is
     * credited the (converted) amount and one ledger row exists. On any failure no balance or
     * ledger row has changed (atomic rollback).</p>
     *
     * @param fromId                source account id
     * @param toId                  destination account id
     * @param amount                amount to send, in the source currency
     * @param authenticatedUsername the logged-in username
     * @return a success message, e.g. "Successfully transferred 100.00 from Account #3 to Account #4"
     * @throws IllegalArgumentException    "Source account not found", "Insufficient funds",
     *                                     "Destination account not found" or a missing exchange rate (404)
     * @throws AccessDeniedException       if the caller does not own the source account (403)
     * @throws InvalidTransactionException if the amount is invalid or both ids are the same wallet (400)
     */
    @Transactional
    public String transferMoney(Long fromId, Long toId, BigDecimal amount, String authenticatedUsername) {
        // 1. Reject zero, negative or more-than-2-decimal amounts before touching the database
        validateAmount(amount);

        // 2. Load the source wallet; only its owner (taken from the JWT, never the body) may send from it
        Account source = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        verifyOwnership(source, authenticatedUsername);

        // A transfer to the same wallet would leave the balance unchanged but still write a ledger row
        if (fromId.equals(toId)) {
            throw new InvalidTransactionException("Cannot transfer money to the same account");
        }

        // 3. Check the balance in the source wallet's own currency
        BigDecimal sourceBalance = source.getBalance() == null ? BigDecimal.ZERO : source.getBalance();
        if (sourceBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        // 4. Database write #1: debit the source
        source.setBalance(sourceBalance.subtract(amount));
        accountRepository.save(source);

        // 5. Look up the destination deliberately AFTER the debit: a missing destination throws here,
        //    and @Transactional rolls the debit back, which demonstrates atomicity
        Account destination = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        // 6. Forex: when the wallets hold different currencies, convert at the current rate.
        //    A missing rate also throws inside the transaction, so the debit is rolled back too
        String sourceCurrency = currencyOf(source);
        String destinationCurrency = currencyOf(destination);
        boolean isForex = !sourceCurrency.equals(destinationCurrency);
        BigDecimal rate = isForex
                ? exchangeRateService.getRate(sourceCurrency, destinationCurrency)
                : BigDecimal.ONE;
        BigDecimal credited = isForex
                ? amount.multiply(rate).setScale(ExchangeRateService.MONEY_SCALE, RoundingMode.HALF_EVEN)
                : amount;

        // 7. Database write #2: credit the destination with the (possibly converted) amount
        BigDecimal destinationBalance = destination.getBalance() == null ? BigDecimal.ZERO : destination.getBalance();
        destination.setBalance(destinationBalance.add(credited));
        accountRepository.save(destination);

        // 8. Database write #3: append the immutable ledger row, keeping the rate used so later
        //    rate changes never rewrite the details of a completed transfer
        TransferRecord record = new TransferRecord();
        record.setSourceAccountId(source.getId() != null ? source.getId() : fromId);
        record.setDestinationAccountId(destination.getId() != null ? destination.getId() : toId);
        record.setAmount(amount);
        record.setSourceCurrency(sourceCurrency);
        record.setDestinationCurrency(destinationCurrency);
        record.setConvertedAmount(credited);
        record.setExchangeRate(rate);
        transferRecordRepository.save(record);

        // 9. Build the confirmation message; forex transfers also report the conversion
        String sent = amount.setScale(ExchangeRateService.MONEY_SCALE, RoundingMode.HALF_EVEN).toPlainString();
        String message = String.format("Successfully transferred %s from Account #%d to Account #%d",
                sent, fromId, toId);
        if (isForex) {
            message += String.format(" (%s %s converted to %s %s at rate %s)",
                    sent, sourceCurrency, credited.toPlainString(), destinationCurrency, rate.toPlainString());
        }
        return message;
    }

    /**
     * Returns the full transfer history (sent and received) of one wallet, newest first.
     *
     * <p><b>Pre-condition:</b> {@code authenticatedUsername} comes from the JWT and owns {@code accountId}.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param accountId             the wallet whose history is requested
     * @param authenticatedUsername the logged-in username
     * @return the ledger entries as DTOs (empty list if none)
     * @throws IllegalArgumentException if the account does not exist (404)
     * @throws AccessDeniedException    if the caller does not own the account (403)
     */
    @Transactional(readOnly = true)
    public List<TransferRecordDTO> getTransferHistory(Long accountId, String authenticatedUsername) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        verifyOwnership(account, authenticatedUsername);

        return transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(accountId, accountId)
                .stream()
                .sorted(Comparator.comparing(TransferRecord::getTransferDate,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .map(record -> convertToDTO(record, accountId))
                .toList();
    }

    /**
     * Ensures the logged-in user owns the account (Account &rarr; Customer &rarr; User &rarr; username).
     *
     * <p><b>Pre-condition:</b> {@code account} is not null.</p>
     * <p><b>Post-condition:</b> returns normally only for the owner.</p>
     *
     * @param account  the account to check
     * @param username the logged-in username
     * @throws AccessDeniedException if the account has no owner or belongs to someone else (403)
     */
    private void verifyOwnership(Account account, String username) {
        String owner = null;
        if (account.getCustomer() != null && account.getCustomer().getUser() != null) {
            owner = account.getCustomer().getUser().getUsername();
        }
        if (owner == null || !owner.equals(username)) {
            throw new AccessDeniedException("You do not own this account");
        }
    }

    /**
     * Rejects null, zero, negative or sub-centavo amounts.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> returns normally only for a valid amount.</p>
     *
     * @param amount the amount to check
     * @throws InvalidTransactionException if the amount is invalid (400)
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Transfer amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > ExchangeRateService.MONEY_SCALE) {
            throw new InvalidTransactionException("Transfer amount cannot have more than 2 decimal places");
        }
    }

    /**
     * Returns the account's currency, treating legacy rows without one as PHP.
     *
     * <p><b>Pre-condition:</b> {@code account} is not null. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param account the account
     * @return its upper-case currency code
     */
    private String currencyOf(Account account) {
        String currency = account.getCurrency();
        return (currency == null || currency.isBlank())
                ? SupportedCurrency.HOME.name()
                : currency.trim().toUpperCase();
    }

    /**
     * Maps a ledger entity to its DTO, labelling it SENT or RECEIVED for the given account.
     *
     * <p><b>Pre-condition:</b> {@code record} is not null. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param record    the ledger entry
     * @param accountId the account whose point of view is used
     * @return the DTO
     */
    private TransferRecordDTO convertToDTO(TransferRecord record, Long accountId) {
        TransferRecordDTO dto = new TransferRecordDTO();
        dto.setId(record.getId());
        dto.setSourceAccountId(record.getSourceAccountId());
        dto.setDestinationAccountId(record.getDestinationAccountId());
        dto.setAmount(record.getAmount());
        dto.setTransferDate(record.getTransferDate());
        dto.setSourceCurrency(record.getSourceCurrency());
        dto.setDestinationCurrency(record.getDestinationCurrency());
        dto.setConvertedAmount(record.getConvertedAmount());
        dto.setExchangeRate(record.getExchangeRate());
        dto.setDirection(accountId.equals(record.getSourceAccountId()) ? "SENT" : "RECEIVED");
        return dto;
    }
}
