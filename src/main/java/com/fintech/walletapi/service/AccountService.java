package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.AccountDTO;
import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.SupportedCurrency;
import com.fintech.walletapi.exception.DuplicateWalletException;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.CustomerRepository;
import com.fintech.walletapi.repository.TransferRecordRepository;
import com.fintech.walletapi.exception.InvalidTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for wallets (accounts): open peso or forex wallets, read,
 * list, update balances and close. Every method returns DTOs, never entities.
 */
@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransferRecordRepository transferRecordRepository;

    /**
     * Creates the service.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param accountRepository  wallet repository
     * @param customerRepository customer repository (to link the owner)
     * @param transferRecordRepository ledger repository (to preserve historical wallets)
     */
    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          TransferRecordRepository transferRecordRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transferRecordRepository = transferRecordRepository;
    }

    /**
     * Opens a new wallet. A currency other than PHP makes it a forex wallet.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN, or a customer opening a wallet under their own
     * {@code customerId}; {@code dto.currency} is null or a supported code; the customer has no
     * wallet in that currency yet.</p>
     * <p><b>Post-condition:</b> a new account row exists, linked to the customer (if given),
     * in the requested currency (PHP when none was given).</p>
     *
     * @param dto account type, opening balance, status, optional currency and customer id
     * @return the saved wallet
     * @throws IllegalArgumentException     if {@code customerId} does not exist (404)
     * @throws DuplicateWalletException     if the customer already has a wallet in that currency (409)
     * @throws com.fintech.walletapi.exception.InvalidTransactionException if the currency is not supported (400)
     * @throws org.springframework.security.access.AccessDeniedException   if the caller may not use that customer id (403)
     */
    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#dto.customerId, authentication.name)")
    public AccountDTO createAccount(AccountDTO dto) {
        // Copy the client-supplied fields onto a new wallet
        Account entity = new Account();
        entity.setAccountType(dto.getAccountType());
        entity.setBalance(dto.getBalance());
        entity.setStatus(dto.getStatus());

        // No currency means PHP; otherwise normalize it ("usd" -> USD) and reject unsupported codes (400)
        String currency = (dto.getCurrency() == null || dto.getCurrency().isBlank())
                ? SupportedCurrency.HOME.name()
                : SupportedCurrency.fromCode(dto.getCurrency()).name();
        entity.setCurrency(currency);

        // Link the owner and enforce one wallet per currency per customer (409 on a duplicate);
        // the unique key on (customer_id, currency) backs this up for simultaneous requests
        if (dto.getCustomerId() != null) {
            Customer owner = customerRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not Found"));
            if (accountRepository.existsByCustomer_IdAndCurrency(dto.getCustomerId(), currency)) {
                throw new DuplicateWalletException("Customer " + dto.getCustomerId()
                        + " already has a " + currency + " wallet");
            }
            entity.setCustomer(owner);
        }

        // Persist, then return a DTO so the entity never leaves the service layer
        Account savedEntity = accountRepository.save(entity);
        return convertToDTO(savedEntity);
    }

    /**
     * Lists every wallet in the system.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @return all wallets
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Lists the wallets of the logged-in user (peso and forex).
     *
     * <p><b>Pre-condition:</b> {@code username} comes from the JWT, never from the request body.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param username the logged-in username
     * @return the user's wallets (empty list if none)
     */
    @Transactional(readOnly = true)
    public List<AccountDTO> getMyAccounts(String username) {
        return accountRepository.findByCustomer_User_Username(username).stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Reads one wallet.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN or the owner (checked after loading).</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param id the account id
     * @return the wallet
     * @throws IllegalArgumentException if no wallet has this id (404)
     * @throws org.springframework.security.access.AccessDeniedException if the caller is not the owner (403)
     */
    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    @Transactional(readOnly = true)
    public AccountDTO getAccountById(Long id) {
        return accountRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("Account not Found"));
    }

    /**
     * Lists wallets with a given status.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param status the status to filter by, e.g. {@code ACTIVE}
     * @return matching wallets
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountByStatus(String status) {
        return accountRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Closes (deletes) a wallet.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> an existing wallet is deleted only if its balance is zero and it has no ledger history.</p>
     * @throws ResponseStatusException 409 when funds or transfer history must be preserved
     *
     * @param id the account id
     * @return {@code true} if a wallet was deleted, {@code false} if none had this id
     */
    @PreAuthorize("hasRole('ADMIN')")
    public boolean closeAccount(Long id) {
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return false;
        }
        if (account.getBalance() == null || account.getBalance().signum() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a zero-balance wallet can be deleted");
        }
        if (!transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(id, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallets with transfer history cannot be deleted");
        }
        accountRepository.delete(account);
        return true;
    }

    /**
     * Overwrites a wallet's balance (manual adjustment by an administrator).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; {@code updatedAccount.balance} is set.</p>
     * <p><b>Post-condition:</b> the wallet's balance equals the new value.</p>
     *
     * @param id             the account id
     * @param updatedAccount carries the new balance
     * @return the updated wallet, or {@code null} if no wallet has this id
     */
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDTO updateAccountBalance(Long id, AccountDTO updatedAccount) {
        return accountRepository.findById(id)
                .map(existingAccount -> {
                    BigDecimal balance = updatedAccount == null ? null : updatedAccount.getBalance();
                    if (balance == null || balance.signum() < 0 || balance.scale() > 2
                            || balance.precision() - balance.scale() > 17) {
                        throw new InvalidTransactionException("Balance must be nonnegative with at most 17 integer digits and 2 decimal places");
                    }
                    existingAccount.setBalance(balance);
                    Account savedAccount = accountRepository.save(existingAccount);
                    return convertToDTO(savedAccount);
                })
                .orElse(null);
    }

    /**
     * Returns one page of wallets.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; {@code page >= 0}, {@code size >= 1}.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param page zero-based page number
     * @param size wallets per page
     * @return the requested page
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AccountDTO> getPaginatedAccounts(int page, int size) {
        if (page < 0 || size < 1) {
            throw new InvalidTransactionException("Page must be nonnegative and size must be positive");
        }
        Pageable pageable = PageRequest.of(page, size);
        return accountRepository.findAll(pageable).map(this::convertToDTO);
    }

    /**
     * Maps an entity to its DTO, walking Account &rarr; Customer &rarr; User for the owner's username.
     *
     * <p><b>Pre-condition:</b> called inside a transaction (the customer is lazily loaded).</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param entity the account entity
     * @return the DTO
     */
    private AccountDTO convertToDTO(Account entity) {
        AccountDTO dto = new AccountDTO();
        dto.setId(entity.getId());
        dto.setAccountType(entity.getAccountType());
        dto.setBalance(entity.getBalance());
        dto.setStatus(entity.getStatus());
        dto.setCurrency(entity.getCurrency() == null ? SupportedCurrency.HOME.name() : entity.getCurrency());
        if (entity.getCustomer() != null) {
            dto.setCustomerId(entity.getCustomer().getId());
            dto.setCustomerName(entity.getCustomer().getFullName());
            if (entity.getCustomer().getUser() != null) {
                dto.setUsername(entity.getCustomer().getUser().getUsername());
            }
        }
        return dto;
    }
}
