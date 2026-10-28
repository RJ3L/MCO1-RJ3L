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

@Service
@Transactional
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final ExchangeRateService exchangeRateService;

    public TransferService(AccountRepository accountRepository,
                           TransferRecordRepository transferRecordRepository,
                           ExchangeRateService exchangeRateService) {
        this.accountRepository = accountRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional
    public String transferMoney(Long fromId, Long toId, BigDecimal amount, String authenticatedUsername) {
        validateAmount(amount);

        Account source = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        verifyOwnership(source, authenticatedUsername);

        if (fromId.equals(toId)) {
            throw new InvalidTransactionException("Cannot transfer money to the same account");
        }

        BigDecimal sourceBalance = source.getBalance() == null ? BigDecimal.ZERO : source.getBalance();
        if (sourceBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        source.setBalance(sourceBalance.subtract(amount));
        accountRepository.save(source);

        Account destination = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        String sourceCurrency = currencyOf(source);
        String destinationCurrency = currencyOf(destination);
        boolean isForex = !sourceCurrency.equals(destinationCurrency);
        BigDecimal rate = isForex
                ? exchangeRateService.getRate(sourceCurrency, destinationCurrency)
                : BigDecimal.ONE;
        BigDecimal credited = isForex
                ? amount.multiply(rate).setScale(ExchangeRateService.MONEY_SCALE, RoundingMode.HALF_EVEN)
                : amount;

        BigDecimal destinationBalance = destination.getBalance() == null ? BigDecimal.ZERO : destination.getBalance();
        destination.setBalance(destinationBalance.add(credited));
        accountRepository.save(destination);

        TransferRecord record = new TransferRecord();
        record.setSourceAccountId(source.getId() != null ? source.getId() : fromId);
        record.setDestinationAccountId(destination.getId() != null ? destination.getId() : toId);
        record.setAmount(amount);
        record.setSourceCurrency(sourceCurrency);
        record.setDestinationCurrency(destinationCurrency);
        record.setConvertedAmount(credited);
        record.setExchangeRate(rate);
        transferRecordRepository.save(record);

        String sent = amount.setScale(ExchangeRateService.MONEY_SCALE, RoundingMode.HALF_EVEN).toPlainString();
        String message = String.format("Successfully transferred %s from Account #%d to Account #%d",
                sent, fromId, toId);
        if (isForex) {
            message += String.format(" (%s %s converted to %s %s at rate %s)",
                    sent, sourceCurrency, credited.toPlainString(), destinationCurrency, rate.toPlainString());
        }
        return message;
    }

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

    private void verifyOwnership(Account account, String username) {
        String owner = null;
        if (account.getCustomer() != null && account.getCustomer().getUser() != null) {
            owner = account.getCustomer().getUser().getUsername();
        }
        if (owner == null || !owner.equals(username)) {
            throw new AccessDeniedException("You do not own this account");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Transfer amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > ExchangeRateService.MONEY_SCALE) {
            throw new InvalidTransactionException("Transfer amount cannot have more than 2 decimal places");
        }
    }

    private String currencyOf(Account account) {
        String currency = account.getCurrency();
        return (currency == null || currency.isBlank())
                ? SupportedCurrency.HOME.name()
                : currency.trim().toUpperCase();
    }

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
