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

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private long ledgerRowsFor(Long accountId) {
        return transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(accountId, accountId).size();
    }

    private String bearer(String username) {
        return "Bearer " + jwtUtil.generateToken(username);
    }

    private static String body(Long from, Long to, String amount) {
        return "{\"fromId\":" + from + ",\"toId\":" + to + ",\"amount\":" + amount + "}";
    }

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

    @Test
    @DisplayName("TC-TRFINT-03 | TransferService.transferMoney (end to end) | Bob moves money out of Alice's wallet -> 403, nothing changes")
    void bobCannotSpendAlicesMoney() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "100.00")))
                .andExpect(status().isForbidden());

        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(alicePeso)));
    }

    @Test
    @DisplayName("TC-TRFINT-04 | TransferService.transferMoney (end to end) | 1000 PHP into own USD forex wallet at 1/50 -> 20.00 USD")
    void forexIntoOwnDollarWallet() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, aliceDollar, "1000.00")))
                .andExpect(status().isOk());

        assertEquals(0, BigDecimal.ZERO.compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("20.00").compareTo(balanceOf(aliceDollar)));
    }

    @Test
    @DisplayName("TC-TRFINT-05 | TransferService.transferMoney (end to end) | no token -> 401")
    void noToken() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "1.00")))
                .andExpect(status().isUnauthorized());
    }

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

    @Test
    @DisplayName("TC-TRFINT-07 | TransferService.getTransferHistory (end to end) | Bob reads Alice's history -> 403; no token -> 401")
    void historyIsPrivate() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/history/" + alicePeso).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transfers/history/" + alicePeso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-TRFINT-08 | TransferService.transferMoney (end to end) | zero amount -> 400 validation error")
    void zeroAmountRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body(alicePeso, bobPeso, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transfer amount must be greater than zero"));
    }

    private int concurrent(Long firstFrom, Long firstTo, String firstUser,
                           Long secondFrom, Long secondTo, String secondUser, String amount) throws Exception {
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
                            accountRepository.findAllById(List.of(firstFrom, firstTo, secondFrom, secondTo));
                            try { ready.await(10, TimeUnit.SECONDS); }
                            catch (Exception ex) { throw new IllegalStateException(ex); }
                            transferService.transferMoney(froms[index], tos[index], new BigDecimal(amount), usernames[index]);
                        });
                        return true;
                    } catch (OptimisticLockingFailureException conflict) {
                        return false;
                    }
                }));
            }
            int committed = 0;
            for (Future<Boolean> result : futures) if (result.get(20, TimeUnit.SECONDS)) committed++;
            return committed;
        } finally { pool.shutdownNow(); }
    }

    @Test
    @DisplayName("TC-TRFINT-09 | TransferService.transferMoney | simultaneous debits on same snapshot -> one commit, no lost debit")
    void simultaneousDebits() throws Exception {
        assertEquals(1, concurrent(alicePeso, bobPeso, alice, alicePeso, bobPeso, alice, "100.00"));
        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(bobPeso)));
        assertEquals(1, ledgerRowsFor(alicePeso));
    }
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
    @Test
    @DisplayName("TC-TRFINT-11 | TransferService.transferMoney | competing 700 transfers from 1000 -> only one succeeds, no overspend")
    void simultaneousOverspendPrevented() throws Exception {
        assertEquals(1, concurrent(alicePeso, bobPeso, alice, alicePeso, bobPeso, alice, "700.00"));
        assertEquals(0, new BigDecimal("300.00").compareTo(balanceOf(alicePeso)));
        assertEquals(0, new BigDecimal("1200.00").compareTo(balanceOf(bobPeso)));
        assertEquals(1, ledgerRowsFor(alicePeso));
    }

}
