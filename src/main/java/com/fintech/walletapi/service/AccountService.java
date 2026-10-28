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

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransferRecordRepository transferRecordRepository;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          TransferRecordRepository transferRecordRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transferRecordRepository = transferRecordRepository;
    }

    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#dto.customerId, authentication.name)")
    public AccountDTO createAccount(AccountDTO dto) {
        Account entity = new Account();
        entity.setAccountType(dto.getAccountType());
        entity.setBalance(dto.getBalance());
        entity.setStatus(dto.getStatus());

        String currency = (dto.getCurrency() == null || dto.getCurrency().isBlank())
                ? SupportedCurrency.HOME.name()
                : SupportedCurrency.fromCode(dto.getCurrency()).name();
        entity.setCurrency(currency);

        if (dto.getCustomerId() != null) {
            Customer owner = customerRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not Found"));
            if (accountRepository.existsByCustomer_IdAndCurrency(dto.getCustomerId(), currency)) {
                throw new DuplicateWalletException("Customer " + dto.getCustomerId()
                        + " already has a " + currency + " wallet");
            }
            entity.setCustomer(owner);
        }

        Account savedEntity = accountRepository.save(entity);
        return convertToDTO(savedEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getMyAccounts(String username) {
        return accountRepository.findByCustomer_User_Username(username).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    @Transactional(readOnly = true)
    public AccountDTO getAccountById(Long id) {
        return accountRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("Account not Found"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountByStatus(String status) {
        return accountRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .toList();
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AccountDTO> getPaginatedAccounts(int page, int size) {
        if (page < 0 || size < 1) {
            throw new InvalidTransactionException("Page must be nonnegative and size must be positive");
        }
        Pageable pageable = PageRequest.of(page, size);
        return accountRepository.findAll(pageable).map(this::convertToDTO);
    }

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
