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

/**
 * Unit tests for {@link AccountService} — pure Mockito, no Spring context, no database.
 */
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

    /**
     * Builds an active savings wallet owned by a customer whose login is {@code username}.
     *
     * <p><b>Pre-condition:</b> {@code balance} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> No state is changed; the customer's id is {@code id * 10}.</p>
     *
     * @param id wallet id
     * @param username owner's login name
     * @param currency currency code
     * @param balance balance as text
     * @return the wallet
     */
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

    /**
     * Builds a request to open an active savings wallet with 1000.00.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param customerId owner's customer id, or {@code null}
     * @param currency currency code, or {@code null} for the default
     * @return the request DTO
     */
    private static AccountDTO newAccountRequest(Long customerId, String currency) {
        AccountDTO dto = new AccountDTO();
        dto.setCustomerId(customerId);
        dto.setAccountType("SAVINGS");
        dto.setBalance(new BigDecimal("1000.00"));
        dto.setStatus("ACTIVE");
        dto.setCurrency(currency);
        return dto;
    }

    /**
     * TC-ACCSVC-01: tests {@code AccountService.getAccountById}.
     *
     * <p><b>Pre-condition:</b> Unknown id 99.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException 'Account not Found'.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-01 | AccountService.getAccountById | unknown id 99 -> IllegalArgumentException 'Account not Found'")
    public void testGetAccountById_NotFound_ThrowsException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> accountService.getAccountById(99L));

        assertEquals("Account not Found", exception.getMessage());
    }

    /**
     * TC-ACCSVC-02: tests {@code AccountService.getAccountById}.
     *
     * <p><b>Pre-condition:</b> Existing id 1.</p>
     * <p><b>Post-condition:</b> DTO with accountType.</p>
     */
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

    /**
     * TC-ACCSVC-03: tests {@code AccountService.getAccountById}.
     *
     * <p><b>Pre-condition:</b> Owned USD wallet.</p>
     * <p><b>Post-condition:</b> DTO carries owner username and currency.</p>
     */
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

    /**
     * TC-ACCSVC-04: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> No currency given.</p>
     * <p><b>Post-condition:</b> PHP wallet linked to customer.</p>
     */
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

    /**
     * TC-ACCSVC-05: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> Currency 'usd'.</p>
     * <p><b>Post-condition:</b> Forex wallet stored as USD.</p>
     */
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

    /**
     * TC-ACCSVC-06: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> Unsupported currency JPY.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException, nothing saved.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-06 | AccountService.createAccount | unsupported currency JPY -> InvalidTransactionException, nothing saved")
    void createAccount_unsupportedCurrency_throws() {
        assertThrows(InvalidTransactionException.class,
                () -> accountService.createAccount(newAccountRequest(5L, "JPY")));
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-07: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> Unknown customerId.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException 'Customer not Found'.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-07 | AccountService.createAccount | unknown customerId -> IllegalArgumentException 'Customer not Found'")
    void createAccount_unknownCustomer_throws() {
        when(customerRepository.findById(404L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.createAccount(newAccountRequest(404L, "PHP")));

        assertEquals("Customer not Found", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-26: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> Customer already has a USD wallet.</p>
     * <p><b>Post-condition:</b> DuplicateWalletException, nothing saved.</p>
     */
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

    /**
     * TC-ACCSVC-27: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> No currency given + existing PHP wallet.</p>
     * <p><b>Post-condition:</b> DuplicateWalletException (default PHP is checked).</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-27 | AccountService.createAccount | no currency given + existing PHP wallet -> DuplicateWalletException (default PHP is checked)")
    void createAccount_duplicateDefaultPhp_throws() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.existsByCustomer_IdAndCurrency(5L, "PHP")).thenReturn(true);

        assertThrows(DuplicateWalletException.class,
                () -> accountService.createAccount(newAccountRequest(5L, null)));
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-28: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> Currency 'eur'.</p>
     * <p><b>Post-condition:</b> Duplicate check uses normalized 'EUR', new currency allowed.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-28 | AccountService.createAccount | currency 'eur' -> duplicate check uses normalized 'EUR', new currency allowed")
    void createAccount_checksNormalizedCurrency() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO result = accountService.createAccount(newAccountRequest(5L, "eur"));

        verify(accountRepository).existsByCustomer_IdAndCurrency(5L, "EUR");
        assertEquals("EUR", result.getCurrency());
    }

    /**
     * TC-ACCSVC-29: tests {@code AccountService.createAccount}.
     *
     * <p><b>Pre-condition:</b> No customerId (admin, unowned wallet).</p>
     * <p><b>Post-condition:</b> No duplicate check, saved.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-29 | AccountService.createAccount | no customerId (admin, unowned wallet) -> no duplicate check, saved")
    void createAccount_noCustomer_skipsDuplicateCheck() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.createAccount(newAccountRequest(null, "PHP"));

        verify(accountRepository, never()).existsByCustomer_IdAndCurrency(any(), any());
        verify(accountRepository).save(any(Account.class));
    }

    /**
     * TC-ACCSVC-08: tests {@code AccountService.getAllAccounts}.
     *
     * <p><b>Pre-condition:</b> Two accounts in DB.</p>
     * <p><b>Post-condition:</b> Two DTOs.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-08 | AccountService.getAllAccounts | two accounts in DB -> two DTOs")
    void getAllAccounts_returnsAll() {
        when(accountRepository.findAll()).thenReturn(List.of(
                ownedAccount(1L, "alice", "PHP", "1.00"), ownedAccount(2L, "bob", "USD", "2.00")));

        List<AccountDTO> result = accountService.getAllAccounts();

        assertEquals(2, result.size());
        assertEquals("bob", result.get(1).getUsername());
    }

    /**
     * TC-ACCSVC-09: tests {@code AccountService.getAllAccounts}.
     *
     * <p><b>Pre-condition:</b> Empty DB.</p>
     * <p><b>Post-condition:</b> Empty list.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-09 | AccountService.getAllAccounts | empty DB -> empty list")
    void getAllAccounts_empty() {
        when(accountRepository.findAll()).thenReturn(List.of());
        assertTrue(accountService.getAllAccounts().isEmpty());
    }

    /**
     * TC-ACCSVC-10: tests {@code AccountService.getAllAccounts}.
     *
     * <p><b>Pre-condition:</b> Account without customer.</p>
     * <p><b>Post-condition:</b> DTO with null owner fields.</p>
     */
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

    /**
     * TC-ACCSVC-11: tests {@code AccountService.getMyAccounts}.
     *
     * <p><b>Pre-condition:</b> Alice owns PHP + USD wallets.</p>
     * <p><b>Post-condition:</b> Both returned.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-11 | AccountService.getMyAccounts | alice owns PHP + USD wallets -> both returned")
    void getMyAccounts_returnsOwnedWallets() {
        when(accountRepository.findByCustomer_User_Username("alice")).thenReturn(List.of(
                ownedAccount(1L, "alice", "PHP", "900.00"), ownedAccount(2L, "alice", "USD", "10.00")));

        List<AccountDTO> result = accountService.getMyAccounts("alice");

        assertEquals(2, result.size());
        assertEquals("USD", result.get(1).getCurrency());
    }

    /**
     * TC-ACCSVC-12: tests {@code AccountService.getMyAccounts}.
     *
     * <p><b>Pre-condition:</b> User with no wallets.</p>
     * <p><b>Post-condition:</b> Empty list.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-12 | AccountService.getMyAccounts | user with no wallets -> empty list")
    void getMyAccounts_none() {
        when(accountRepository.findByCustomer_User_Username("carol")).thenReturn(List.of());
        assertTrue(accountService.getMyAccounts("carol").isEmpty());
    }

    /**
     * TC-ACCSVC-13: tests {@code AccountService.getMyAccounts}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Queries the repository with the given username only.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-13 | AccountService.getMyAccounts | queries the repository with the given username only")
    void getMyAccounts_usesUsername() {
        when(accountRepository.findByCustomer_User_Username("bob")).thenReturn(List.of());
        accountService.getMyAccounts("bob");
        verify(accountRepository).findByCustomer_User_Username("bob");
        verify(accountRepository, never()).findAll();
    }

    /**
     * TC-ACCSVC-14: tests {@code AccountService.getAccountByStatus}.
     *
     * <p><b>Pre-condition:</b> ACTIVE.</p>
     * <p><b>Post-condition:</b> Matching accounts.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-14 | AccountService.getAccountByStatus | ACTIVE -> matching accounts")
    void getAccountByStatus_active() {
        when(accountRepository.findByStatus("ACTIVE")).thenReturn(List.of(ownedAccount(1L, "alice", "PHP", "5.00")));
        assertEquals(1, accountService.getAccountByStatus("ACTIVE").size());
    }

    /**
     * TC-ACCSVC-15: tests {@code AccountService.getAccountByStatus}.
     *
     * <p><b>Pre-condition:</b> FROZEN with no matches.</p>
     * <p><b>Post-condition:</b> Empty list.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-15 | AccountService.getAccountByStatus | FROZEN with no matches -> empty list")
    void getAccountByStatus_noMatch() {
        when(accountRepository.findByStatus("FROZEN")).thenReturn(List.of());
        assertTrue(accountService.getAccountByStatus("FROZEN").isEmpty());
    }

    /**
     * TC-ACCSVC-16: tests {@code AccountService.getAccountByStatus}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Status value passed to repository unchanged.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-16 | AccountService.getAccountByStatus | status value passed to repository unchanged")
    void getAccountByStatus_passesStatus() {
        when(accountRepository.findByStatus("CLOSED")).thenReturn(List.of());
        accountService.getAccountByStatus("CLOSED");
        verify(accountRepository).findByStatus("CLOSED");
    }

    /**
     * TC-ACCSVC-17: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> True and deleted.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-17 | AccountService.closeAccount | existing id -> true and deleted")
    void closeAccount_existing() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "0.00")));
        assertTrue(accountService.closeAccount(1L));
        verify(accountRepository).delete(any(Account.class));
    }

    /**
     * TC-ACCSVC-18: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> False, nothing deleted.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-18 | AccountService.closeAccount | unknown id -> false, nothing deleted")
    void closeAccount_missing() {
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());
        assertFalse(accountService.closeAccount(2L));
        verify(accountRepository, never()).delete(any());
    }

    /**
     * TC-ACCSVC-19: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Deletes only the requested id.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-19 | AccountService.closeAccount | deletes only the requested id")
    void closeAccount_onlyRequestedId() {
        Account eligible = ownedAccount(3L, "alice", "PHP", "0.00");
        when(accountRepository.findById(3L)).thenReturn(Optional.of(eligible));
        accountService.closeAccount(3L);
        verify(accountRepository, times(1)).delete(eligible);
    }

    /**
     * TC-ACCSVC-20: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> Balance replaced.</p>
     */
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

    /**
     * TC-ACCSVC-21: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> Null, nothing saved.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-21 | AccountService.updateAccountBalance | unknown id -> null, nothing saved")
    void updateAccountBalance_missing() {
        when(accountRepository.findById(9L)).thenReturn(Optional.empty());
        assertNull(accountService.updateAccountBalance(9L, new AccountDTO()));
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-22: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Currency and owner are not changed.</p>
     */
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

    /**
     * TC-ACCSVC-23: tests {@code AccountService.getPaginatedAccounts}.
     *
     * <p><b>Pre-condition:</b> Page 0 size 2.</p>
     * <p><b>Post-condition:</b> Page of DTOs.</p>
     */
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

    /**
     * TC-ACCSVC-24: tests {@code AccountService.getPaginatedAccounts}.
     *
     * <p><b>Pre-condition:</b> Page beyond data.</p>
     * <p><b>Post-condition:</b> Empty page.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-24 | AccountService.getPaginatedAccounts | page beyond data -> empty page")
    void getPaginatedAccounts_emptyPage() {
        Pageable pageable = PageRequest.of(10, 5);
        when(accountRepository.findAll(pageable)).thenReturn(Page.empty(pageable));
        assertTrue(accountService.getPaginatedAccounts(10, 5).getContent().isEmpty());
    }

    /**
     * TC-ACCSVC-25: tests {@code AccountService.getPaginatedAccounts}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Page/size forwarded as PageRequest.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-25 | AccountService.getPaginatedAccounts | page/size forwarded as PageRequest")
    void getPaginatedAccounts_forwardsPageRequest() {
        when(accountRepository.findAll(PageRequest.of(1, 3))).thenReturn(Page.empty(PageRequest.of(1, 3)));
        accountService.getPaginatedAccounts(1, 3);
        verify(accountRepository).findAll(PageRequest.of(1, 3));
    }

    /**
     * TC-ACCSVC-30: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Funded wallet.</p>
     * <p><b>Post-condition:</b> Conflict, no deletion.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-30 | AccountService.closeAccount | funded wallet -> conflict, no deletion")
    void closeFundedWalletRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "1.00")));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> accountService.closeAccount(1L)).getStatusCode().value());
        verify(accountRepository, never()).delete(any());
    }

    /**
     * TC-ACCSVC-31: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Zero balance with history.</p>
     * <p><b>Post-condition:</b> Conflict, history preserved.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-31 | AccountService.closeAccount | zero balance with history -> conflict, history preserved")
    void closeHistoricalWalletRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "0.00")));
        when(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(1L, 1L)).thenReturn(List.of(new TransferRecord()));
        assertThrows(ResponseStatusException.class, () -> accountService.closeAccount(1L));
        verify(accountRepository, never()).delete(any());
    }

    /**
     * TC-ACCSVC-32: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> Negative balance.</p>
     * <p><b>Post-condition:</b> Rejected without persistence.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-32 | AccountService.updateAccountBalance | negative balance -> rejected without persistence")
    void negativeAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        AccountDTO dto = new AccountDTO(); dto.setBalance(new BigDecimal("-1"));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, dto));
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-33: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> Missing balance.</p>
     * <p><b>Post-condition:</b> Rejected without persistence.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-33 | AccountService.updateAccountBalance | missing balance -> rejected without persistence")
    void nullAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, new AccountDTO()));
        verify(accountRepository, never()).save(any());
    }

    /**
     * TC-ACCSVC-34: tests {@code AccountService.updateAccountBalance}.
     *
     * <p><b>Pre-condition:</b> Fractional cent.</p>
     * <p><b>Post-condition:</b> Rejected without persistence.</p>
     */
    @Test
    @DisplayName("TC-ACCSVC-34 | AccountService.updateAccountBalance | fractional cent -> rejected without persistence")
    void precisionAdjustmentRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ownedAccount(1L, "alice", "PHP", "10.00")));
        AccountDTO dto = new AccountDTO(); dto.setBalance(new BigDecimal("1.001"));
        assertThrows(InvalidTransactionException.class, () -> accountService.updateAccountBalance(1L, dto));
        verify(accountRepository, never()).save(any());
    }

}
