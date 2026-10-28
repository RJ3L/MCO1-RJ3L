package com.fintech.walletapi.service;

import com.fintech.walletapi.client.FrankfurterClient;
import com.fintech.walletapi.client.FrankfurterClient.LatestRate;
import com.fintech.walletapi.dto.ForexSyncResultDTO;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.exception.ForexProviderUnavailableException;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForexRateSyncServiceTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 10, 26);

    @Mock
    private FrankfurterClient frankfurterClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private ForexRateSyncService forexRateSyncService;

    private void stubLive(String base, String rate) {
        when(frankfurterClient.fetchLatestRate(base, "PHP")).thenReturn(new LatestRate(new BigDecimal(rate), RATE_DATE));
    }

    private void stubAllLive() {
        stubLive("USD", "63.944");
        stubLive("EUR", "71.54");
        stubLive("GBP", "85.35");
        stubLive("SGD", "49.93");
    }

    private void saveReturnsArgument() {
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("TC-FXSYNC-01 | ForexRateSyncService.syncRates | existing seeded USD->PHP 58 -> overwritten with 63.944000")
    void sync_updatesExistingRow() {
        stubAllLive();
        saveReturnsArgument();
        ExchangeRate seeded = new ExchangeRate("USD", "PHP", new BigDecimal("58.000000"));
        seeded.setId(1L);
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(anyString(), eq("PHP"))).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("USD", "PHP")).thenReturn(Optional.of(seeded));

        forexRateSyncService.syncRates();

        assertEquals(new BigDecimal("63.944000"), seeded.getRate());
        verify(exchangeRateRepository).save(seeded);
    }

    @Test
    @DisplayName("TC-FXSYNC-02 | ForexRateSyncService.syncRates | empty table -> 4 new X->PHP rows inserted")
    void sync_insertsMissingRows() {
        stubAllLive();
        saveReturnsArgument();
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);

        ForexSyncResultDTO result = forexRateSyncService.syncRates();

        verify(exchangeRateRepository, times(4)).save(saved.capture());
        assertEquals(List.of("USD", "EUR", "GBP", "SGD"),
                saved.getAllValues().stream().map(ExchangeRate::getBaseCurrency).toList());
        assertTrue(saved.getAllValues().stream().allMatch(r -> r.getQuoteCurrency().equals("PHP")));
        assertEquals(4, result.getUpdatedRates().size());
        assertTrue(result.getFailedCurrencies().isEmpty());
    }

    @Test
    @DisplayName("TC-FXSYNC-03 | ForexRateSyncService.syncRates | PHP is the home currency -> never fetched")
    void sync_skipsHomeCurrency() {
        stubAllLive();
        saveReturnsArgument();

        forexRateSyncService.syncRates();

        verify(frankfurterClient, never()).fetchLatestRate(eq("PHP"), anyString());
        verify(frankfurterClient, times(4)).fetchLatestRate(anyString(), eq("PHP"));
    }

    @Test
    @DisplayName("TC-FXSYNC-04 | ForexRateSyncService.syncRates | result carries provider name and ECB rate date")
    void sync_resultMetadata() {
        stubAllLive();
        saveReturnsArgument();

        ForexSyncResultDTO result = forexRateSyncService.syncRates();

        assertEquals(FrankfurterClient.PROVIDER_NAME, result.getProvider());
        assertEquals(RATE_DATE, result.getRateDate());
        assertEquals(new BigDecimal("71.540000"), result.getUpdatedRates().get(1).getRate());
    }

    @Test
    @DisplayName("TC-FXSYNC-05 | ForexRateSyncService.syncRates | GBP fetch fails -> others saved, GBP listed as failed")
    void sync_partialFailure() {
        stubLive("USD", "63.944");
        stubLive("EUR", "71.54");
        when(frankfurterClient.fetchLatestRate("GBP", "PHP")).thenThrow(new ResourceAccessException("timeout"));
        stubLive("SGD", "49.93");
        saveReturnsArgument();

        ForexSyncResultDTO result = forexRateSyncService.syncRates();

        assertEquals(List.of("GBP"), result.getFailedCurrencies());
        assertEquals(3, result.getUpdatedRates().size());
        verify(exchangeRateRepository, never()).findByBaseCurrencyAndQuoteCurrency("GBP", "PHP");
    }

    @Test
    @DisplayName("TC-FXSYNC-06 | ForexRateSyncService.syncRates | provider unreachable -> ForexProviderUnavailableException, nothing saved")
    void sync_allFail() {
        when(frankfurterClient.fetchLatestRate(anyString(), eq("PHP"))).thenThrow(new ResourceAccessException("offline"));

        assertThrows(ForexProviderUnavailableException.class, () -> forexRateSyncService.syncRates());
        verify(exchangeRateRepository, never()).save(any());
    }
}
