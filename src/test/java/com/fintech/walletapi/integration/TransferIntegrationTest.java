package com.fintech.walletapi.integration;

import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import com.fintech.walletapi.repository.TransferRecordRepository;
import com.fintech.walletapi.repository.UserRepository;
import com.fintech.walletapi.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import com.fintech.walletapi.service.TransferService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack transfer tests with the REAL security chain (JWT filter on) and a
 * real H2 database. Deliberately NOT {@code @Transactional}: each HTTP call runs
 * in its own service transaction, exactly like production, so we can observe
 * commits and rollbacks. Test data is removed in {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransferRecordRepository transferRecordRepository;
    @Autowired private ExchangeRateRepository exchangeRateRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TransferService transferService;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> rateIds = new ArrayList<>();

    private String alice;
    private String bob;
    private Long alicePeso;
    private Long aliceDollar;
    private Long bobPeso;

    /**
     * Saves a user with a profile and the password {@code password123}, tracking it for clean-up.
     *
     * <p><b>Pre-condition:</b> The username is unused.</p>
     * <p><b>Post-condition:</b> The user exists and its id is recorded in {@code userIds}.</p>
     *
     * @param username login name
     * @param fullName profile name
     * @return the saved user
     */
    private User createUser(String username, String fullName) {
        Customer customer = new Customer();
        customer.setFullName(fullName);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setCustomer(customer);
        customer.setUser(user);
        User saved = userRepository.save(user);
        userIds.add(saved.getId());
        return saved;
    }

    /**
     * Saves an active savings wallet for a user, tracking it for clean-up.
     *
     * <p><b>Pre-condition:</b> {@code owner} is saved; {@code balance} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> The wallet exists and its id is recorded in {@code accountIds}.</p>
     *
     * @param owner the owning user
     * @param currency currency code
     * @param balance opening balance as text
     * @return the new wallet's id
     */
    private Long createWallet(User owner, String currency, String balance) {
        Account account = new Account();
        account.setAccountType("SAVINGS");
        account.setStatus("ACTIVE");
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        account.setCustomer(owner.getCustomer());
        Long id = accountRepository.save(account).getId();
        accountIds.add(id);
        return id;
    }

    /**
     * Reads a wallet's committed balance from the database.
     *
     * <p><b>Pre-condition:</b> The wallet exists.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param accountId wallet id
     * @return the stored balance
     */
    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    /**
     * Counts the ledger rows where a wallet is sender or receiver.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param accountId wallet id
     * @return the number of transfer records
     */
    private long ledgerRowsFor(Long accountId) {
        return transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(accountId, accountId).size();
    }

    /**
     * Builds an Authorization header value with a freshly signed JWT.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param username the token's subject
     * @return {@code "Bearer <token>"}
     */
    private String bearer(String username) {
        return "Bearer " + jwtUtil.generateToken(username);
    }

    /**
     * Builds a transfer request body.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param from source wallet id
     * @param to destination wallet id
     * @param amount amount as JSON text
     * @return the request body
     */
    private static String body(Long from, Long to, String amount) {
        return "{\"fromId\":" + from + ",\"toId\":" + to + ",\"amount\":" + amount + "}";
    }

    /**
     * Creates alice (PHP 1000, USD 0), bob (PHP 500) with unique usernames, and a USD-&gt;PHP 50 rate if none
     * exists.
     *
     * <p><b>Pre-condition:</b> A new test is about to run; this class commits real transactions.</p>
     * <p><b>Post-condition:</b> The wallets and rate exist and are tracked for clean-up.</p>
     */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = "it-alice-" + suffix;
        bob = "it-bob-" + suffix;
        User aliceUser = createUser(alice, "Alice Integration");
        User bobUser = createUser(bob, "Bob Integration");
        alicePeso = createWallet(aliceUser, "PHP", "1000.00");
        aliceDollar = createWallet(aliceUser, "USD", "0.00");
        bobPeso = createWallet(bobUser, "PHP", "500.00");
        if (!exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("USD", "PHP")) {
            rateIds.add(exchangeRateRepository.save(new ExchangeRate("USD", "PHP", new BigDecimal("50.000000"))).getId());
        }
    }

    /**
     * Deletes everything the test created: ledger rows, wallets, users and the added rate.
     *
     * <p><b>Pre-condition:</b> The test has finished.</p>
     * <p><b>Post-condition:</b> The database is back to its state before the test; the tracking lists are
     * empty.</p>
     */
    @AfterEach
    void cleanUp() {
        transferRecordRepository.findAll().stream()
                .filter(r -> accountIds.contains(r.getSourceAccountId()) || accountIds.contains(r.getDestinationAccountId()))
                .forEach(transferRecordRepository::delete);
        accountIds.forEach(accountRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
        rateIds.forEach(exchangeRateRepository::deleteById);
        accountIds.clear();
        userIds.clear();
        rateIds.clear();
    }

    /**
     * TC-TRFINT-01: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> Alice sends 100 PHP to Bob.</p>
     * <p><b>Post-condition:</b> 200, 900/600, one ledger row.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-01 | TransferService.transferMoney (end to end) | Alice sends 100 PHP to Bob -> 200, 900/600, one ledger row")
    void happyPath() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "100.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer successful"));

        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(bobPeso)));
        assertEquals(1, ledgerRowsFor(alicePeso));
    }

    /**
     * TC-TRFINT-02: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> To missing account 999999.</p>
     * <p><b>Post-condition:</b> 404 and debit ROLLED BACK.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-02 | TransferService.transferMoney (end to end) | to missing account 999999 -> 404 and debit ROLLED BACK")
    void rollbackOnMissingDestination() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, 999999L, "100.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination account not found"));

        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, ledgerRowsFor(alicePeso));
    }

    /**
     * TC-TRFINT-03: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> Bob moves money out of Alice's wallet.</p>
     * <p><b>Post-condition:</b> 403, nothing changes.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-03 | TransferService.transferMoney (end to end) | Bob moves money out of Alice's wallet -> 403, nothing changes")
    void bobCannotSpendAlicesMoney() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "100.00")))
                .andExpect(status().isForbidden());

        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(alicePeso)));
    }

    /**
     * TC-TRFINT-04: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> 1000 PHP into own USD forex wallet at 1/50.</p>
     * <p><b>Post-condition:</b> 20.00 USD.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-04 | TransferService.transferMoney (end to end) | 1000 PHP into own USD forex wallet at 1/50 -> 20.00 USD")
    void forexIntoOwnDollarWallet() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, aliceDollar, "1000.00")))
                .andExpect(status().isOk());

        assertEquals(0, BigDecimal.ZERO.compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("20.00").compareTo(balanceOf(aliceDollar)));
    }

    /**
     * TC-TRFINT-05: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> No token.</p>
     * <p><b>Post-condition:</b> 401.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-05 | TransferService.transferMoney (end to end) | no token -> 401")
    void noToken() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "1.00")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-TRFINT-06: tests {@code TransferService.getTransferHistory (end to end)}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Shows the successful transfer only, not the failed one.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-06 | TransferService.getTransferHistory (end to end) | shows the successful transfer only, not the failed one")
    void historyShowsOnlySuccessfulTransfers() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "100.00")));
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, 999999L, "100.00")));

        mockMvc.perform(get("/api/v1/transfers/history/" + alicePeso).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].destinationAccountId").value(bobPeso))
                .andExpect(jsonPath("$.data[0].direction").value("SENT"));
    }

    /**
     * TC-TRFINT-07: tests {@code TransferService.getTransferHistory (end to end)}.
     *
     * <p><b>Pre-condition:</b> Bob reads Alice's history.</p>
     * <p><b>Post-condition:</b> 403; no token -&gt; 401.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-07 | TransferService.getTransferHistory (end to end) | Bob reads Alice's history -> 403; no token -> 401")
    void historyIsPrivate() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/history/" + alicePeso).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transfers/history/" + alicePeso))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TC-TRFINT-08: tests {@code TransferService.transferMoney (end to end)}.
     *
     * <p><b>Pre-condition:</b> Zero amount.</p>
     * <p><b>Post-condition:</b> 400 validation error.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-08 | TransferService.transferMoney (end to end) | zero amount -> 400 validation error")
    void zeroAmountRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transfer amount must be greater than zero"));
    }

    /**
     * Runs two transfers at the same moment, each in its own transaction, after both have read the wallets.
     *
     * <p><b>Pre-condition:</b> The wallets exist; a barrier makes both threads read the same versions before
     * either writes.</p>
     * <p><b>Post-condition:</b> At most one conflicting transfer commits; a stale write is rolled back by
     * optimistic locking.</p>
     *
     * @param firstFrom first transfer's source
     * @param firstTo first transfer's destination
     * @param firstUser first sender
     * @param secondFrom second transfer's source
     * @param secondTo second transfer's destination
     * @param secondUser second sender
     * @param amount amount for both transfers
     * @return how many of the two transfers committed
     * @throws Exception if a thread fails for a reason other than an optimistic-locking conflict
     */
    private int concurrent(Long firstFrom, Long firstTo, String firstUser,
                           Long secondFrom, Long secondTo, String secondUser, String amount) throws Exception {
        // The barrier holds both threads until each has read the wallets, so both start from the same versions
        CyclicBarrier ready = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            Long[] froms = {firstFrom, secondFrom}, tos = {firstTo, secondTo};
            String[] usernames = {firstUser, secondUser};
            for (int i = 0; i < 2; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    try {
                        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                            // Load every wallet involved inside this transaction before waiting
                            accountRepository.findAllById(List.of(firstFrom, firstTo, secondFrom, secondTo));
                            try { ready.await(10, TimeUnit.SECONDS); }
                            catch (Exception ex) { throw new IllegalStateException(ex); }
                            transferService.transferMoney(froms[index], tos[index], new BigDecimal(amount), usernames[index]);
                        });
                        return true;
                    // The slower transaction lost the version check and was rolled back
                    } catch (OptimisticLockingFailureException conflict) {
                        return false;
                    }
                }));
            }
            // Count how many of the two transfers actually committed
            int committed = 0;
            for (Future<Boolean> result : futures) if (result.get(20, TimeUnit.SECONDS)) committed++;
            return committed;
        } finally { pool.shutdownNow(); }
    }

    /**
     * TC-TRFINT-09: tests {@code TransferService.transferMoney}.
     *
     * <p><b>Pre-condition:</b> Simultaneous debits on same snapshot.</p>
     * <p><b>Post-condition:</b> One commit, no lost debit.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-09 | TransferService.transferMoney | simultaneous debits on same snapshot -> one commit, no lost debit")
    void simultaneousDebits() throws Exception {
        assertEquals(1, concurrent(alicePeso, bobPeso, alice, alicePeso, bobPeso, alice, "100.00"));
        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(bobPeso)));
        assertEquals(1, ledgerRowsFor(alicePeso));
    }
    /**
     * TC-TRFINT-10: tests {@code TransferService.transferMoney}.
     *
     * <p><b>Pre-condition:</b> Simultaneous credits from different wallets.</p>
     * <p><b>Post-condition:</b> Conflicting transfer entirely rolls back.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-10 | TransferService.transferMoney | simultaneous credits from different wallets -> conflicting transfer entirely rolls back")
    void simultaneousCredits() throws Exception {
        User receiver = createUser("receiver-" + UUID.randomUUID(), "Receiver");
        Long destination = createWallet(receiver, "PHP", "0.00");
        assertEquals(1, concurrent(alicePeso, destination, alice, bobPeso, destination, bob, "100.00"));
        assertEquals(0, new BigDecimal("100.00").compareTo(balanceOf(destination)));
        assertEquals(0, new BigDecimal("1400.00").compareTo(balanceOf(alicePeso).add(balanceOf(bobPeso))));
        assertEquals(1, ledgerRowsFor(destination));
    }
    /**
     * TC-TRFINT-11: tests {@code TransferService.transferMoney}.
     *
     * <p><b>Pre-condition:</b> Competing 700 transfers from 1000.</p>
     * <p><b>Post-condition:</b> Only one succeeds, no overspend.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-TRFINT-11 | TransferService.transferMoney | competing 700 transfers from 1000 -> only one succeeds, no overspend")
    void simultaneousOverspendPrevented() throws Exception {
        assertEquals(1, concurrent(alicePeso, bobPeso, alice, alicePeso, bobPeso, alice, "700.00"));
        assertEquals(0, new BigDecimal("300.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("1200.00").compareTo(balanceOf(bobPeso)));
        assertEquals(1, ledgerRowsFor(alicePeso));
    }

}
