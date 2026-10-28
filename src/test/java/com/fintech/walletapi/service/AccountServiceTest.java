package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.AccountDTO;
import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.exception.DuplicateWalletException;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.CustomerRepository;
import com.fintech.walletapi.repository.TransferRecordRepository;
import com.fintech.walletapi.entity.TransferRecord;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransferRecordRepository transferRecordRepository;

    @InjectMocks
    private AccountService accountService;

    private static Account ownedAccount(Long id, String username, String currency, String balance) {
        User user = new User();
        user.setUsername(username);
        Customer customer = new Customer();
        customer.setId(id * 10);
        customer.setFullName(username + " Customer");
        customer.setUser(user);
        user.setCustomer(customer);
        Account account = new Account();
        account.setId(id);
        account.setAccountType("SAVINGS");
        account.setStatus("ACTIVE");
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        account.setCustomer(customer);
        return account;
    }

    private static AccountDTO newAccountRequest(Long customerId, String currency) {
        AccountDTO dto = new AccountDTO();
        dto.setCustomerId(customerId);
        dto.setAccountType("SAVINGS");
        dto.setBalance(new BigDecimal("1000.00"));
        dto.setStatus("ACTIVE");
        dto.setCurrency(currency);
        return dto;
    }

    @Test
    @DisplayName("TC-ACCSVC-01 | AccountService.getAccountById | unknown id 99 -> IllegalArgumentException 'Account not Found'")
    public void testGetAccountById_NotFound_ThrowsException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> accountService.getAccountById(99L));

        assertEquals("Account not Found", exception.getMessage());
    }

    @Test
    @DisplayName("TC-ACCSVC-02 | AccountService.getAccountById | existing id 1 -> DTO with accountType")
    public void testGetAccountById_Found_ReturnsDTO() {
        Account account = new Account();
        account.setId(1L);
        account.setAccountType("CHECKING");
        account.setBalance(new BigDecimal("250.00"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountDTO result = accountService.getAccountById(1L);

        assertNotNull(result);
        assertEquals("CHECKING", result.getAccountType());
        assertEquals("PHP", result.getCurrency(), "legacy rows without a currency are shown as PHP");
    }

    @Test
    @DisplayName("TC-ACCSVC-03 | AccountService.getAccountById | owned USD wallet -> DTO carries owner username and currency")
    void getAccountById_ownedForexWallet_mapsOwnerAndCurrency() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(ownedAccount(7L, "alice", "USD", "20.00")));

        AccountDTO result = accountService.getAccountById(7L);

        assertEquals("alice", result.getUsername());
        assertEquals("USD", result.getCurrency());
        assertEquals(70L, result.getCustomerId());
        assertEquals("alice Customer", result.getCustomerName());
    }

    @Test
    @DisplayName("TC-ACCSVC-04 | AccountService.createAccount | no currency given -> PHP wallet linked to customer")
    void createAccount_defaultsToPhp() {
        Customer owner = new Customer();
        owner.setId(5L);
        when(customerRepository.findById(5L)).thenReturn(Optional.of(owner));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.createAccount(newAccountRequest(5L, null));

        assertEquals("PHP", result.getCurrency());
        assertEquals(5L, result.getCustomerId());
        assertEquals(new BigDecimal("1000.00"), result.getBalance());
    }

    @Test
    @DisplayName("TC-ACCSVC-05 | AccountService.createAccount | currency 'usd' -> forex wallet stored as USD")
    void createAccount_forexWallet_normalizesCurrency() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.createAccount(newAccountRequest(5L, "usd"));

        assertEquals("USD", captor.getValue().getCurrency());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    @DisplayName("TC-ACCSVC-06 | AccountService.createAccount | unsupported currency JPY -> InvalidTransactionException, nothing saved")
    void createAccount_unsupportedCurrency_throws() {
        assertThrows(InvalidTransactionException.class,
                () -> accountService.createAccount(newAccountRequest(5L, "JPY")));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-07 | AccountService.createAccount | unknown customerId -> IllegalArgumentException 'Customer not Found'")
    void createAccount_unknownCustomer_throws() {
        when(customerRepository.findById(404L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.createAccount(newAccountRequest(404L, "PHP")));

        assertEquals("Customer not Found", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-26 | AccountService.createAccount | customer already has a USD wallet -> DuplicateWalletException, nothing saved")
    void createAccount_duplicateCurrency_throws() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.existsByCustomer_IdAndCurrency(5L, "USD")).thenReturn(true);

        Exception ex = assertThrows(DuplicateWalletException.class,
                () -> accountService.createAccount(newAccountRequest(5L, "USD")));

        assertEquals("Customer 5 already has a USD wallet", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-27 | AccountService.createAccount | no currency given + existing PHP wallet -> DuplicateWalletException (default PHP is checked)")
    void createAccount_duplicateDefaultPhp_throws() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.existsByCustomer_IdAndCurrency(5L, "PHP")).thenReturn(true);

        assertThrows(DuplicateWalletException.class,
                () -> accountService.createAccount(newAccountRequest(5L, null)));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-28 | AccountService.createAccount | currency 'eur' -> duplicate check uses normalized 'EUR', new currency allowed")
    void createAccount_checksNormalizedCurrency() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.createAccount(newAccountRequest(5L, "eur"));

        verify(accountRepository).existsByCustomer_IdAndCurrency(5L, "EUR");
        assertEquals("EUR", result.getCurrency());
    }

    @Test
    @DisplayName("TC-ACCSVC-29 | AccountService.createAccount | no customerId (admin, unowned wallet) -> no duplicate check, saved")
    void createAccount_noCustomer_skipsDuplicateCheck() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.createAccount(newAccountRequest(null, "PHP"));

        verify(accountRepository, never()).existsByCustomer_IdAndCurrency(any(), any());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("TC-ACCSVC-08 | AccountService.getAllAccounts | two accounts in DB -> two DTOs")
    void getAllAccounts_returnsAll() {
        when(accountRepository.findAll()).thenReturn(List.of(
                ownedAccount(1L, "alice", "PHP", "1.00"), ownedAccount(2L, "bob", "USD", "2.00")));

        List<AccountDTO> result = accountService.getAllAccounts();

        assertEquals(2, result.size());
        assertEquals("bob", result.get(1).getUsername());
    }

    @Test
    @DisplayName("TC-ACCSVC-09 | AccountService.getAllAccounts | empty DB -> empty list")
    void getAllAccounts_empty() {
        when(accountRepository.findAll()).thenReturn(List.of());
        assertTrue(accountService.getAllAccounts().isEmpty());
    }

    @Test
    @DisplayName("TC-ACCSVC-10 | AccountService.getAllAccounts | account without customer -> DTO with null owner fields")
    void getAllAccounts_unlinkedAccount() {
        Account orphan = new Account();
        orphan.setId(3L);
        orphan.setAccountType("CHECKING");
        when(accountRepository.findAll()).thenReturn(List.of(orphan));

        AccountDTO dto = accountService.getAllAccounts().get(0);

        assertNull(dto.getUsername());
        assertNull(dto.getCustomerId());
    }

    @Test
    @DisplayName("TC-ACCSVC-11 | AccountService.getMyAccounts | alice owns PHP + USD wallets -> both returned")
    void getMyAccounts_returnsOwnedWallets() {
        when(accountRepository.findByCustomer_User_Username("alice")).thenReturn(List.of(
                ownedAccount(1L, "alice", "PHP", "900.00"), ownedAccount(2L, "alice", "USD", "10.00")));

        List<AccountDTO> result = accountService.getMyAccounts("alice");

        assertEquals(2, result.size());
        assertEquals("USD", result.get(1).getCurrency());
    }

    @Test
    @DisplayName("TC-ACCSVC-12 | AccountService.getMyAccounts | user with no wallets -> empty list")
    void getMyAccounts_none() {
        when(accountRepository.findByCustomer_User_Username("carol")).thenReturn(List.of());
        assertTrue(accountService.getMyAccounts("carol").isEmpty());
    }

    @Test
    @DisplayName("TC-ACCSVC-13 | AccountService.getMyAccounts | queries the repository with the given username only")
    void getMyAccounts_usesUsername() {
        when(accountRepository.findByCustomer_User_Username("bob")).thenReturn(List.of());
        accountService.getMyAccounts("bob");
        verify(accountRepository).findByCustomer_User_Username("bob");
        verify(accountRepository, never()).findAll();
    }

    @Test
    @DisplayName("TC-ACCSVC-14 | AccountService.getAccountByStatus | ACTIVE -> matching accounts")
    void getAccountByStatus_active() {
        when(accountRepository.findByStatus("ACTIVE")).thenReturn(List.of(ownedAccount(1L, "alice", "PHP", "5.00")));
        assertEquals(1, accountService.getAccountByStatus("ACTIVE").size());
    }

    @Test
    @DisplayName("TC-ACCSVC-15 | AccountService.getAccountByStatus | FROZEN with no matches -> empty list")
    void getAccountByStatus_noMatch() {
        when(accountRepository.findByStatus("FROZEN")).thenReturn(List.of());
        assertTrue(accountService.getAccountByStatus("FROZEN").isEmpty());
    }

    @Test
    @DisplayName("TC-ACCSVC-16 | AccountService.getAccountByStatus | status value passed to repository unchanged")
    void getAccountByStatus_passesStatus() {
        when(accountRepository.findByStatus("CLOSED")).thenReturn(List.of());
        accountService.getAccountByStatus("CLOSED");
        verify(accountRepository).findByStatus("CLOSED");
    }

    @Test
    @DisplayName("TC-ACCSVC-17 | AccountService.closeAccount | existing id -> true and deleted")
    void closeAccount_existing() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "0.00")));
        assertTrue(accountService.closeAccount(1L));
        verify(accountRepository).delete(any(Account.class));
    }

    @Test
    @DisplayName("TC-ACCSVC-18 | AccountService.closeAccount | unknown id -> false, nothing deleted")
    void closeAccount_missing() {
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());
        assertFalse(accountService.closeAccount(2L));
        verify(accountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-19 | AccountService.closeAccount | deletes only the requested id")
    void closeAccount_onlyRequestedId() {
        Account eligible = ownedAccount(3L, "alice", "PHP", "0.00");
        when(accountRepository.findById(3L)).thenReturn(Optional.of(eligible));
        accountService.closeAccount(3L);
        verify(accountRepository, times(1)).delete(eligible);
    }

    @Test
    @DisplayName("TC-ACCSVC-20 | AccountService.updateAccountBalance | existing id -> balance replaced")
    void updateAccountBalance_existing() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "100.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        AccountDTO body = new AccountDTO();
        body.setBalance(new BigDecimal("900.00"));

        AccountDTO result = accountService.updateAccountBalance(1L, body);

        assertEquals(new BigDecimal("900.00"), result.getBalance());
    }

    @Test
    @DisplayName("TC-ACCSVC-21 | AccountService.updateAccountBalance | unknown id -> null, nothing saved")
    void updateAccountBalance_missing() {
        when(accountRepository.findById(9L)).thenReturn(Optional.empty());
        assertNull(accountService.updateAccountBalance(9L, new AccountDTO()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-22 | AccountService.updateAccountBalance | currency and owner are not changed")
    void updateAccountBalance_keepsOtherFields() {
        when(accountRepository.findById(2L)).thenReturn(Optional.of(ownedAccount(2L, "bob", "USD", "5.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        AccountDTO body = new AccountDTO();
        body.setBalance(new BigDecimal("50.00"));
        body.setCurrency("EUR");

        AccountDTO result = accountService.updateAccountBalance(2L, body);

        assertEquals("USD", result.getCurrency());
        assertEquals("bob", result.getUsername());
    }

    @Test
    @DisplayName("TC-ACCSVC-23 | AccountService.getPaginatedAccounts | page 0 size 2 -> page of DTOs")
    void getPaginatedAccounts_firstPage() {
        Pageable pageable = PageRequest.of(0, 2);
        when(accountRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(
                ownedAccount(1L, "alice", "PHP", "1.00"), ownedAccount(2L, "bob", "PHP", "2.00")), pageable, 5));

        Page<AccountDTO> page = accountService.getPaginatedAccounts(0, 2);

        assertEquals(2, page.getContent().size());
        assertEquals(5, page.getTotalElements());
    }

    @Test
    @DisplayName("TC-ACCSVC-24 | AccountService.getPaginatedAccounts | page beyond data -> empty page")
    void getPaginatedAccounts_emptyPage() {
        Pageable pageable = PageRequest.of(10, 5);
        when(accountRepository.findAll(pageable)).thenReturn(Page.empty(pageable));
        assertTrue(accountService.getPaginatedAccounts(10, 5).getContent().isEmpty());
    }

    @Test
    @DisplayName("TC-ACCSVC-25 | AccountService.getPaginatedAccounts | page/size forwarded as PageRequest")
    void getPaginatedAccounts_forwardsPageRequest() {
        when(accountRepository.findAll(PageRequest.of(1, 3))).thenReturn(Page.empty(PageRequest.of(1, 3)));
        accountService.getPaginatedAccounts(1, 3);
        verify(accountRepository).findAll(PageRequest.of(1, 3));
    }

    @Test
    @DisplayName("TC-ACCSVC-30 | AccountService.closeAccount | funded wallet -> conflict, no deletion")
    void closeFundedWalletRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "1.00")));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> accountService.closeAccount(1L)).getStatusCode().value());
        verify(accountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-31 | AccountService.closeAccount | zero balance with history -> conflict, history preserved")
    void closeHistoricalWalletRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "0.00")));
        when(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(1L, 1L)).thenReturn(List.of(new TransferRecord()));
        assertThrows(ResponseStatusException.class, () -> accountService.closeAccount(1L));
        verify(accountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-32 | AccountService.updateAccountBalance | negative balance -> rejected without persistence")
    void negativeAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        AccountDTO dto = new AccountDTO(); dto.setBalance(new BigDecimal("-1"));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, dto));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-33 | AccountService.updateAccountBalance | missing balance -> rejected without persistence")
    void nullAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, new AccountDTO()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ACCSVC-34 | AccountService.updateAccountBalance | fractional cent -> rejected without persistence")
    void precisionAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        AccountDTO dto = new AccountDTO(); dto.setBalance(new BigDecimal("1.001"));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, dto));
        verify(accountRepository, never()).save(any());
    }

}
