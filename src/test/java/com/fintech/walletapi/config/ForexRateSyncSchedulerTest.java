package com.fintech.walletapi.config;

import com.fintech.walletapi.client.FrankfurterClient;
import com.fintech.walletapi.dto.ForexSyncResultDTO;
import com.fintech.walletapi.exception.ForexProviderUnavailableException;
import com.fintech.walletapi.service.ForexRateSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForexRateSyncSchedulerTest {

    @Mock
    private ForexRateSyncService forexRateSyncService;

    @InjectMocks
    private ForexRateSyncScheduler scheduler;

    private static ForexSyncResultDTO ok() {
        return new ForexSyncResultDTO(FrankfurterClient.PROVIDER_NAME, LocalDate.of(2026, 10, 26), List.of(), List.of());
    }

    @Test
    @DisplayName("TC-FXSCH-01 | ForexRateSyncScheduler.syncOnStartup | application ready -> one sync is run")
    void startup_runsSync() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnStartup();
        verify(forexRateSyncService, times(1)).syncRates();
    }

    @Test
    @DisplayName("TC-FXSCH-02 | ForexRateSyncScheduler.syncOnStartup | provider down -> startup continues, no exception")
    void startup_providerDown() {
        when(forexRateSyncService.syncRates()).thenThrow(new ForexProviderUnavailableException("down"));
        assertDoesNotThrow(() -> scheduler.syncOnStartup());
    }

    @Test
    @DisplayName("TC-FXSCH-03 | ForexRateSyncScheduler.syncOnStartup | unexpected bug -> not swallowed (propagates)")
    void startup_unexpectedError() {
        when(forexRateSyncService.syncRates()).thenThrow(new IllegalStateException("bug"));
        assertThrows(IllegalStateException.class, () -> scheduler.syncOnStartup());
    }

    @Test
    @DisplayName("TC-FXSCH-04 | ForexRateSyncScheduler.syncOnSchedule | cron fires -> one sync is run")
    void schedule_runsSync() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnSchedule();
        verify(forexRateSyncService, times(1)).syncRates();
    }

    @Test
    @DisplayName("TC-FXSCH-05 | ForexRateSyncScheduler.syncOnSchedule | provider down -> logged, scheduler keeps running")
    void schedule_providerDown() {
        when(forexRateSyncService.syncRates()).thenThrow(new ForexProviderUnavailableException("down"));
        assertDoesNotThrow(() -> scheduler.syncOnSchedule());
        verify(forexRateSyncService).syncRates();
    }

    @Test
    @DisplayName("TC-FXSCH-06 | ForexRateSyncScheduler.syncOnSchedule | fires twice -> two independent syncs")
    void schedule_twice() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnSchedule();
        scheduler.syncOnSchedule();
        verify(forexRateSyncService, times(2)).syncRates();
    }
}
