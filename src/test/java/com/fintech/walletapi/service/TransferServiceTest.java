package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.TransferRecordDTO;
import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.TransferRecord;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.TransferRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRecordRepository transferRecordRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private TransferService transferService;

    private static Account wallet(Long id, String owner, String currency, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        if (owner != null) {
            User user = new User();
            user.setUsername(owner);
            Customer customer = new Customer();
            customer.setUser(user);
            user.setCustomer(customer);
            account.setCustomer(customer);
        }
        return account;
    }

    private static TransferRecord record(Long id, Long from, Long to, String amount, LocalDateTime when) {
        TransferRecord r = new TransferRecord();
        r.setId(id);
        r.setSourceAccountId(from);
        r.setDestinationAccountId(to);
        r.setAmount(new BigDecimal(amount));
        r.setTransferDate(when);
        r.setSourceCurrency("PHP");
        r.setDestinationCurrency("PHP");
        r.setConvertedAmount(new BigDecimal(amount));
        r.setExchangeRate(BigDecimal.ONE);
        return r;
    }

    @Test
    @DisplayName("TC-TRFSVC-01 | TransferService.transferMoney | Alice sends 100.00 PHP to Bob -> 900/600 and one ledger row")
    void transferMoney_happyPath() {
        Account alice = wallet(3L, "alice", "PHP", "1000.00");
        Account bob = wallet(4L, "bob", "PHP", "500.00");
        when(accountRepository.findById(3L)).thenReturn(Optional.of(alice));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(bob));
        ArgumentCaptor<TransferRecord> ledger = ArgumentCaptor.forClass(TransferRecord.class);

        String result = transferService.transferMoney(3L, 4L, new BigDecimal("100.00"), "alice");

        assertEquals("Successfully transferred 100.00 from Account #3 to Account #4", result);
        assertEquals(new BigDecimal("900.00"), alice.getBalance());
        assertEquals(new BigDecimal("600.00"), bob.getBalance());
        verify(transferRecordRepository).save(ledger.capture());
        assertEquals(3L, ledger.getValue().getSourceAccountId());
        assertEquals(4L, ledger.getValue().getDestinationAccountId());
        assertEquals(new BigDecimal("100.00"), ledger.getValue().getAmount());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("TC-TRFSVC-02 | TransferService.transferMoney | unknown source -> 'Source account not found', nothing written")
    void transferMoney_sourceMissing() {
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferMoney(3L, 4L, new BigDecimal("10"), "alice"));

        assertEquals("Source account not found", ex.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transferRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-03 | TransferService.transferMoney | Bob sends from Alice's account -> AccessDeniedException (403)")
    void transferMoney_notOwner() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1000.00")));

        assertThrows(AccessDeniedException.class,
                () -> transferService.transferMoney(3L, 4L, new BigDecimal("10"), "bob"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-04 | TransferService.transferMoney | 2000 from a 1000 balance -> 'Insufficient funds', nothing written")
    void transferMoney_insufficientFunds() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1000.00")));

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferMoney(3L, 4L, new BigDecimal("2000.00"), "alice"));

        assertEquals("Insufficient funds", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-05 | TransferService.transferMoney | destination 999 missing -> 'Destination account not found', no ledger row")
    void transferMoney_destinationMissing() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1000.00")));
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferMoney(3L, 999L, new BigDecimal("100.00"), "alice"));

        assertEquals("Destination account not found", ex.getMessage());
        verify(accountRepository, times(1)).save(any());
        verify(transferRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-06 | TransferService.transferMoney | 1000 PHP into a USD forex wallet at 0.017241 -> 17.24 USD credited")
    void transferMoney_forexConversion() {
        Account peso = wallet(3L, "alice", "PHP", "5000.00");
        Account dollars = wallet(8L, "alice", "USD", "0.00");
        when(accountRepository.findById(3L)).thenReturn(Optional.of(peso));
        when(accountRepository.findById(8L)).thenReturn(Optional.of(dollars));
        when(exchangeRateService.getRate("PHP", "USD")).thenReturn(new BigDecimal("0.017241"));
        ArgumentCaptor<TransferRecord> ledger = ArgumentCaptor.forClass(TransferRecord.class);

        String result = transferService.transferMoney(3L, 8L, new BigDecimal("1000.00"), "alice");

        assertEquals(new BigDecimal("4000.00"), peso.getBalance());
        assertEquals(new BigDecimal("17.24"), dollars.getBalance());
        assertTrue(result.contains("converted to 17.24 USD"), result);
        verify(transferRecordRepository).save(ledger.capture());
        assertEquals("PHP", ledger.getValue().getSourceCurrency());
        assertEquals("USD", ledger.getValue().getDestinationCurrency());
        assertEquals(new BigDecimal("17.24"), ledger.getValue().getConvertedAmount());
        assertEquals(new BigDecimal("0.017241"), ledger.getValue().getExchangeRate());
    }

    @Test
    @DisplayName("TC-TRFSVC-07 | TransferService.transferMoney | fromId equals toId -> InvalidTransactionException")
    void transferMoney_sameAccount() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1000.00")));

        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> transferService.transferMoney(3L, 3L, new BigDecimal("1.00"), "alice"));

        assertEquals("Cannot transfer money to the same account", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-08 | TransferService.transferMoney | amount 0 -> InvalidTransactionException, DB untouched")
    void transferMoney_zeroAmount() {
        assertThrows(InvalidTransactionException.class,
                () -> transferService.transferMoney(3L, 4L, BigDecimal.ZERO, "alice"));
        verifyNoInteractions(accountRepository, transferRecordRepository);
    }

    @Test
    @DisplayName("TC-TRFSVC-09 | TransferService.transferMoney | amount 10.005 (3 decimals) -> InvalidTransactionException")
    void transferMoney_tooManyDecimals() {
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> transferService.transferMoney(3L, 4L, new BigDecimal("10.005"), "alice"));
        assertEquals("Transfer amount cannot have more than 2 decimal places", ex.getMessage());
    }

    @Test
    @DisplayName("TC-TRFSVC-10 | TransferService.transferMoney | source account without an owner -> AccessDeniedException")
    void transferMoney_unownedSource() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, null, "PHP", "1000.00")));
        assertThrows(AccessDeniedException.class,
                () -> transferService.transferMoney(3L, 4L, BigDecimal.TEN, "alice"));
    }

    @Test
    @DisplayName("TC-TRFSVC-11 | TransferService.transferMoney | no GBP->SGD rate -> exception propagates, no credit, no ledger row")
    void transferMoney_missingRate() {
        Account pounds = wallet(5L, "alice", "GBP", "100.00");
        Account sgd = wallet(6L, "bob", "SGD", "0.00");
        when(accountRepository.findById(5L)).thenReturn(Optional.of(pounds));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(sgd));
        when(exchangeRateService.getRate("GBP", "SGD"))
                .thenThrow(new IllegalArgumentException("Exchange rate not found for GBP to SGD"));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transferMoney(5L, 6L, BigDecimal.TEN, "alice"));

        assertEquals(new BigDecimal("0.00"), sgd.getBalance());
        verify(transferRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-TRFSVC-12 | TransferService.transferMoney | legacy wallets without currency treated as PHP -> no conversion")
    void transferMoney_legacyCurrencyDefaultsToPhp() {
        Account a = wallet(3L, "alice", null, "50.00");
        Account b = wallet(4L, "bob", "PHP", "0.00");
        when(accountRepository.findById(3L)).thenReturn(Optional.of(a));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(b));

        transferService.transferMoney(3L, 4L, new BigDecimal("50.00"), "alice");

        assertEquals(new BigDecimal("50.00"), b.getBalance());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("TC-TRFSVC-13 | TransferService.getTransferHistory | owner -> entries newest first, labelled SENT/RECEIVED")
    void getTransferHistory_owner() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1.00")));
        LocalDateTime now = LocalDateTime.now();
        when(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(3L, 3L)).thenReturn(List.of(
                record(1L, 3L, 4L, "100.00", now.minusDays(1)),
                record(2L, 4L, 3L, "25.00", now)));

        List<TransferRecordDTO> history = transferService.getTransferHistory(3L, "alice");

        assertEquals(2, history.size());
        assertEquals(2L, history.get(0).getId());
        assertEquals("RECEIVED", history.get(0).getDirection());
        assertEquals("SENT", history.get(1).getDirection());
    }

    @Test
    @DisplayName("TC-TRFSVC-14 | TransferService.getTransferHistory | Bob reads Alice's history -> AccessDeniedException, ledger not queried")
    void getTransferHistory_notOwner() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1.00")));

        assertThrows(AccessDeniedException.class, () -> transferService.getTransferHistory(3L, "bob"));
        verifyNoInteractions(transferRecordRepository);
    }

    @Test
    @DisplayName("TC-TRFSVC-15 | TransferService.getTransferHistory | unknown account -> 'Account not found'")
    void getTransferHistory_missingAccount() {
        when(accountRepository.findById(77L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.getTransferHistory(77L, "alice"));

        assertEquals("Account not found", ex.getMessage());
    }

    @Test
    @DisplayName("TC-TRFSVC-16 | TransferService.getTransferHistory | owner with no transfers -> empty list")
    void getTransferHistory_empty() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(wallet(3L, "alice", "PHP", "1.00")));
        when(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(3L, 3L)).thenReturn(List.of());

        assertTrue(transferService.getTransferHistory(3L, "alice").isEmpty());
    }

    @Test
    @DisplayName("TC-TRFSVC-17 | TransferService.getTransferHistory | received forex entry -> RECEIVED with both currencies, converted amount and rate")
    void getTransferHistory_receivedForexEntry() {
        when(accountRepository.findById(4L)).thenReturn(Optional.of(wallet(4L, "alice", "USD", "17.24")));
        TransferRecord forex = record(7L, 3L, 4L, "1000.00", LocalDateTime.now());
        forex.setDestinationCurrency("USD");
        forex.setConvertedAmount(new BigDecimal("17.24"));
        forex.setExchangeRate(new BigDecimal("0.017241"));
        when(transferRecordRepository.findBySourceAccountIdOrDestinationAccountId(4L, 4L)).thenReturn(List.of(forex));

        TransferRecordDTO entry = transferService.getTransferHistory(4L, "alice").get(0);

        assertEquals("RECEIVED", entry.getDirection());
        assertEquals("PHP", entry.getSourceCurrency());
        assertEquals("USD", entry.getDestinationCurrency());
        assertEquals(new BigDecimal("1000.00"), entry.getAmount());
        assertEquals(new BigDecimal("17.24"), entry.getConvertedAmount());
        assertEquals(new BigDecimal("0.017241"), entry.getExchangeRate());
    }
}

