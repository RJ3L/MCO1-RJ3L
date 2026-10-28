package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.TransferRecord;
import com.fintech.walletapi.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the custom (derived) query methods of every repository against H2.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransferRecordRepository transferRecordRepository;
    @Autowired private ExchangeRateRepository exchangeRateRepository;

    /**
     * Saves a user with a linked customer profile.
     *
     * <p><b>Pre-condition:</b> The username is unused.</p>
     * <p><b>Post-condition:</b> A user and a customer row exist (rolled back after the test).</p>
     *
     * @param username login name
     * @param fullName profile name
     * @return the saved user
     */
    private User user(String username, String fullName) {
        Customer c = new Customer();
        c.setFullName(fullName);
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setCustomer(c);
        c.setUser(u);
        return userRepository.save(u);
    }

    /**
     * Saves a savings wallet with balance 10.
     *
     * <p><b>Pre-condition:</b> {@code owner} is saved or {@code null} for an unowned wallet.</p>
     * <p><b>Post-condition:</b> A wallet row exists (rolled back after the test).</p>
     *
     * @param owner owner, or {@code null}
     * @param status wallet status
     * @param currency currency code
     * @return the saved wallet
     */
    private Account wallet(Customer owner, String status, String currency) {
        Account a = new Account();
        a.setAccountType("SAVINGS");
        a.setStatus(status);
        a.setCurrency(currency);
        a.setBalance(BigDecimal.TEN);
        a.setCustomer(owner);
        return accountRepository.save(a);
    }

    /**
     * Saves a ledger row of amount 1 between two wallet ids.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> A transfer record exists (rolled back after the test).</p>
     *
     * @param from source wallet id
     * @param to destination wallet id
     * @return the saved record
     */
    private TransferRecord ledger(Long from, Long to) {
        TransferRecord r = new TransferRecord();
        r.setSourceAccountId(from);
        r.setDestinationAccountId(to);
        r.setAmount(BigDecimal.ONE);
        return transferRecordRepository.save(r);
    }

    /**
     * TC-REPO-01: tests {@code AccountRepository.findByStatus}.
     *
     * <p><b>Pre-condition:</b> One FROZEN-TEST wallet.</p>
     * <p><b>Post-condition:</b> Found.</p>
     */
    @Test
    @DisplayName("TC-REPO-01 | AccountRepository.findByStatus | one FROZEN-TEST wallet -> found")
    void findByStatus_match() {
        wallet(null, "FROZEN-TEST", "PHP");
        assertEquals(1, accountRepository.findByStatus("FROZEN-TEST").size());
    }

    /**
     * TC-REPO-02: tests {@code AccountRepository.findByStatus}.
     *
     * <p><b>Pre-condition:</b> Unused status.</p>
     * <p><b>Post-condition:</b> Empty.</p>
     */
    @Test
    @DisplayName("TC-REPO-02 | AccountRepository.findByStatus | unused status -> empty")
    void findByStatus_none() {
        assertTrue(accountRepository.findByStatus("NO-SUCH-STATUS").isEmpty());
    }

    /**
     * TC-REPO-03: tests {@code AccountRepository.findByStatus}.
     *
     * <p><b>Pre-condition:</b> Mixed statuses.</p>
     * <p><b>Post-condition:</b> Only exact matches.</p>
     */
    @Test
    @DisplayName("TC-REPO-03 | AccountRepository.findByStatus | mixed statuses -> only exact matches")
    void findByStatus_exact() {
        wallet(null, "REPO-A", "PHP");
        wallet(null, "REPO-A", "USD");
        wallet(null, "REPO-B", "PHP");
        assertEquals(2, accountRepository.findByStatus("REPO-A").size());
    }

    /**
     * TC-REPO-04: tests {@code AccountRepository.findByCustomer_User_Username}.
     *
     * <p><b>Pre-condition:</b> User with PHP + USD wallets.</p>
     * <p><b>Post-condition:</b> 2.</p>
     */
    @Test
    @DisplayName("TC-REPO-04 | AccountRepository.findByCustomer_User_Username | user with PHP + USD wallets -> 2")
    void findByUsername_two() {
        User u = user("repo-alice", "Repo Alice");
        wallet(u.getCustomer(), "ACTIVE", "PHP");
        wallet(u.getCustomer(), "ACTIVE", "USD");
        assertEquals(2, accountRepository.findByCustomer_User_Username("repo-alice").size());
    }

    /**
     * TC-REPO-05: tests {@code AccountRepository.findByCustomer_User_Username}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Other user's wallets excluded.</p>
     */
    @Test
    @DisplayName("TC-REPO-05 | AccountRepository.findByCustomer_User_Username | other user's wallets excluded")
    void findByUsername_isolated() {
        User a = user("repo-a", "A");
        User b = user("repo-b", "B");
        wallet(a.getCustomer(), "ACTIVE", "PHP");
        wallet(b.getCustomer(), "ACTIVE", "PHP");
        assertEquals(1, accountRepository.findByCustomer_User_Username("repo-b").size());
    }

    /**
     * TC-REPO-06: tests {@code AccountRepository.findByCustomer_User_Username}.
     *
     * <p><b>Pre-condition:</b> Unknown user.</p>
     * <p><b>Post-condition:</b> Empty.</p>
     */
    @Test
    @DisplayName("TC-REPO-06 | AccountRepository.findByCustomer_User_Username | unknown user -> empty")
    void findByUsername_unknown() {
        assertTrue(accountRepository.findByCustomer_User_Username("repo-nobody").isEmpty());
    }

    /**
     * TC-REPO-07: tests {@code CustomerRepository.findByFullNameContaining}.
     *
     * <p><b>Pre-condition:</b> 'Zyx' inside 'Zyxwv Reyes'.</p>
     * <p><b>Post-condition:</b> Found.</p>
     */
    @Test
    @DisplayName("TC-REPO-07 | CustomerRepository.findByFullNameContaining | 'Zyx' inside 'Zyxwv Reyes' -> found")
    void nameContaining_match() {
        user("repo-z", "Zyxwv Reyes");
        assertEquals(1, customerRepository.findByFullNameContaining("Zyx").size());
    }

    /**
     * TC-REPO-08: tests {@code CustomerRepository.findByFullNameContaining}.
     *
     * <p><b>Pre-condition:</b> No match.</p>
     * <p><b>Post-condition:</b> Empty.</p>
     */
    @Test
    @DisplayName("TC-REPO-08 | CustomerRepository.findByFullNameContaining | no match -> empty")
    void nameContaining_none() {
        assertTrue(customerRepository.findByFullNameContaining("QQQ-NOT-A-NAME").isEmpty());
    }

    /**
     * TC-REPO-09: tests {@code CustomerRepository.findByFullNameContaining}.
     *
     * <p><b>Pre-condition:</b> Keyword in two names.</p>
     * <p><b>Post-condition:</b> Both.</p>
     */
    @Test
    @DisplayName("TC-REPO-09 | CustomerRepository.findByFullNameContaining | keyword in two names -> both")
    void nameContaining_many() {
        user("repo-m1", "Marquez Uno");
        user("repo-m2", "Dos Marquez");
        assertEquals(2, customerRepository.findByFullNameContaining("Marquez").size());
    }

    /**
     * TC-REPO-10: tests {@code UserRepository.findByUsername}.
     *
     * <p><b>Pre-condition:</b> Existing.</p>
     * <p><b>Post-condition:</b> Present.</p>
     */
    @Test
    @DisplayName("TC-REPO-10 | UserRepository.findByUsername | existing -> present")
    void findUser_present() {
        user("repo-find", "Find Me");
        assertTrue(userRepository.findByUsername("repo-find").isPresent());
    }

    /**
     * TC-REPO-11: tests {@code UserRepository.findByUsername}.
     *
     * <p><b>Pre-condition:</b> Missing.</p>
     * <p><b>Post-condition:</b> Empty.</p>
     */
    @Test
    @DisplayName("TC-REPO-11 | UserRepository.findByUsername | missing -> empty")
    void findUser_missing() {
        assertTrue(userRepository.findByUsername("repo-missing").isEmpty());
    }

    /**
     * TC-REPO-12: tests {@code UserRepository.findByUsername}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Profile is linked to the found user.</p>
     */
    @Test
    @DisplayName("TC-REPO-12 | UserRepository.findByUsername | profile is linked to the found user")
    void findUser_linkedProfile() {
        user("repo-link", "Linked Person");
        assertEquals("Linked Person", userRepository.findByUsername("repo-link").orElseThrow().getCustomer().getFullName());
    }

    /**
     * TC-REPO-13: tests {@code TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId}.
     *
     * <p><b>Pre-condition:</b> Sent + received.</p>
     * <p><b>Post-condition:</b> Both.</p>
     */
    @Test
    @DisplayName("TC-REPO-13 | TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId | sent + received -> both")
    void ledger_bothDirections() {
        ledger(90001L, 90002L);
        ledger(90003L, 90001L);
        assertEquals(2, transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(90001L, 90001L).size());
    }

    /**
     * TC-REPO-14: tests {@code TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Unrelated rows excluded.</p>
     */
    @Test
    @DisplayName("TC-REPO-14 | TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId | unrelated rows excluded")
    void ledger_excludesOthers() {
        ledger(90011L, 90012L);
        ledger(90013L, 90014L);
        assertEquals(1, transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(90011L, 90011L).size());
    }

    /**
     * TC-REPO-15: tests {@code TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId}.
     *
     * <p><b>Pre-condition:</b> No transfers.</p>
     * <p><b>Post-condition:</b> Empty; transferDate auto-set.</p>
     */
    @Test
    @DisplayName("TC-REPO-15 | TransferRecordRepository.findBySourceAccountIdOrDestinationAccountId | no transfers -> empty; transferDate auto-set")
    void ledger_emptyAndTimestamp() {
        assertTrue(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(90099L, 90099L).isEmpty());
        assertNotNull(ledger(90021L, 90022L).getTransferDate());
    }

    /**
     * TC-REPO-16: tests {@code ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> Stored SGD-&gt;USD.</p>
     * <p><b>Post-condition:</b> Found.</p>
     */
    @Test
    @DisplayName("TC-REPO-16 | ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency | stored SGD->USD -> found")
    void rate_find() {
        exchangeRateRepository.save(new ExchangeRate("SGD", "USD", new BigDecimal("0.74")));
        assertTrue(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("SGD", "USD").isPresent());
    }

    /**
     * TC-REPO-17: tests {@code ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> Reverse direction is a different row.</p>
     * <p><b>Post-condition:</b> Empty.</p>
     */
    @Test
    @DisplayName("TC-REPO-17 | ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency | reverse direction is a different row -> empty")
    void rate_findReverse() {
        exchangeRateRepository.save(new ExchangeRate("SGD", "EUR", new BigDecimal("0.68")));
        assertTrue(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("EUR", "SGD").isEmpty());
    }

    /**
     * TC-REPO-18: tests {@code ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> UpdatedAt stamped on save.</p>
     */
    @Test
    @DisplayName("TC-REPO-18 | ExchangeRateRepository.findByBaseCurrencyAndQuoteCurrency | updatedAt stamped on save")
    void rate_findTimestamp() {
        exchangeRateRepository.save(new ExchangeRate("GBP", "EUR", new BigDecimal("1.17")));
        assertNotNull(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("GBP", "EUR").orElseThrow().getUpdatedAt());
    }

    /**
     * TC-REPO-19: tests {@code ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> Stored pair.</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-REPO-19 | ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency | stored pair -> true")
    void rate_existsTrue() {
        exchangeRateRepository.save(new ExchangeRate("EUR", "GBP", new BigDecimal("0.85")));
        assertTrue(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("EUR", "GBP"));
    }

    /**
     * TC-REPO-20: tests {@code ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> Missing pair.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-REPO-20 | ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency | missing pair -> false")
    void rate_existsFalse() {
        assertFalse(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("GBP", "SGD"));
    }

    /**
     * TC-REPO-21: tests {@code ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency}.
     *
     * <p><b>Pre-condition:</b> Pair deleted.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-REPO-21 | ExchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency | pair deleted -> false")
    void rate_existsAfterDelete() {
        ExchangeRate saved = exchangeRateRepository.save(new ExchangeRate("USD", "SGD", new BigDecimal("1.35")));
        exchangeRateRepository.delete(saved);
        assertFalse(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("USD", "SGD"));
    }

    /**
     * TC-REPO-22: tests {@code AccountRepository.existsByCustomer_IdAndCurrency}.
     *
     * <p><b>Pre-condition:</b> Customer holds a USD wallet.</p>
     * <p><b>Post-condition:</b> True for USD.</p>
     */
    @Test
    @DisplayName("TC-REPO-22 | AccountRepository.existsByCustomer_IdAndCurrency | customer holds a USD wallet -> true for USD")
    void existsByCustomerAndCurrency_match() {
        User u = user("repo-dup-1", "Dup One");
        wallet(u.getCustomer(), "ACTIVE", "USD");
        assertTrue(accountRepository.existsByCustomer_IdAndCurrency(u.getCustomer().getId(), "USD"));
    }

    /**
     * TC-REPO-23: tests {@code AccountRepository.existsByCustomer_IdAndCurrency}.
     *
     * <p><b>Pre-condition:</b> Customer holds USD only.</p>
     * <p><b>Post-condition:</b> False for EUR.</p>
     */
    @Test
    @DisplayName("TC-REPO-23 | AccountRepository.existsByCustomer_IdAndCurrency | customer holds USD only -> false for EUR")
    void existsByCustomerAndCurrency_otherCurrency() {
        User u = user("repo-dup-2", "Dup Two");
        wallet(u.getCustomer(), "ACTIVE", "USD");
        assertFalse(accountRepository.existsByCustomer_IdAndCurrency(u.getCustomer().getId(), "EUR"));
    }

    /**
     * TC-REPO-26: tests {@code AccountRepository.existsByCustomer_IdAndCurrency}.
     *
     * <p><b>Pre-condition:</b> Another customer's USD wallet.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-REPO-26 | AccountRepository.existsByCustomer_IdAndCurrency | another customer's USD wallet -> false")
    void existsByCustomerAndCurrency_otherCustomer() {
        User owner = user("repo-dup-3", "Dup Three");
        User other = user("repo-dup-5", "Dup Five");
        wallet(owner.getCustomer(), "ACTIVE", "USD");
        assertFalse(accountRepository.existsByCustomer_IdAndCurrency(other.getCustomer().getId(), "USD"));
    }

    /**
     * TC-REPO-24: tests {@code Account unique key (customer_id, currency)}.
     *
     * <p><b>Pre-condition:</b> Second PHP wallet saved directly.</p>
     * <p><b>Post-condition:</b> DataIntegrityViolationException.</p>
     */
    @Test
    @DisplayName("TC-REPO-24 | Account unique key (customer_id, currency) | second PHP wallet saved directly -> DataIntegrityViolationException")
    void uniqueKey_rejectsSecondWalletInSameCurrency() {
        User u = user("repo-dup-4", "Dup Four");
        wallet(u.getCustomer(), "ACTIVE", "PHP");
        Account second = new Account();
        second.setAccountType("CHECKING");
        second.setStatus("ACTIVE");
        second.setCurrency("PHP");
        second.setBalance(BigDecimal.ONE);
        second.setCustomer(u.getCustomer());
        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(second));
    }

    /**
     * TC-REPO-25: tests {@code Account unique key (customer_id, currency)}.
     *
     * <p><b>Pre-condition:</b> Two unowned PHP wallets.</p>
     * <p><b>Post-condition:</b> Both saved (NULL owner not unique).</p>
     */
    @Test
    @DisplayName("TC-REPO-25 | Account unique key (customer_id, currency) | two unowned PHP wallets -> both saved (NULL owner not unique)")
    void uniqueKey_allowsUnownedWallets() {
        wallet(null, "REPO-NULL", "PHP");
        assertDoesNotThrow(() -> {
            wallet(null, "REPO-NULL", "PHP");
            accountRepository.flush();
        });
        assertEquals(2, accountRepository.findByStatus("REPO-NULL").size());
    }

    /**
     * TC-REPO-27: tests {@code AccountRepository.existsByCustomer_Id}.
     *
     * <p><b>Pre-condition:</b> Owner with wallet.</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-REPO-27 | AccountRepository.existsByCustomer_Id | owner with wallet -> true")
    void customerWithWalletExists() {
        Customer c = user("exists-owner", "Owner").getCustomer(); wallet(c, "ACTIVE", "PHP");
        assertTrue(accountRepository.existsByCustomer_Id(c.getId()));
    }
    /**
     * TC-REPO-28: tests {@code AccountRepository.existsByCustomer_Id}.
     *
     * <p><b>Pre-condition:</b> Customer without wallets.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-REPO-28 | AccountRepository.existsByCustomer_Id | customer without wallets -> false")
    void customerWithoutWallets() {
        assertFalse(accountRepository.existsByCustomer_Id(user("empty-owner", "Owner").getCustomer().getId()));
    }
    /**
     * TC-REPO-29: tests {@code AccountRepository.existsByCustomer_Id}.
     *
     * <p><b>Pre-condition:</b> Another customer's wallet.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-REPO-29 | AccountRepository.existsByCustomer_Id | another customer's wallet -> false")
    void otherCustomersWalletDoesNotMatch() {
        Customer a = user("first-owner", "First").getCustomer();
        Customer b = user("second-owner", "Second").getCustomer(); wallet(a, "ACTIVE", "PHP");
        assertFalse(accountRepository.existsByCustomer_Id(b.getId()));
    }

}
