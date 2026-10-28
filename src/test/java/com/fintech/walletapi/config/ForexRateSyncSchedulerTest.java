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

/**
 * Unit tests for {@link ForexRateSyncScheduler}: both triggers delegate to
 * {@link ForexRateSyncService}, and a provider outage never escapes into the scheduler thread.
 */
@ExtendWith(MockitoExtension.class)
class ForexRateSyncSchedulerTest {

    @Mock
    private ForexRateSyncService forexRateSyncService;

    @InjectMocks
    private ForexRateSyncScheduler scheduler;

    /**
     * Builds a successful, empty sync result.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @return a result from the Frankfurter provider with no updates and no failures
     */
    private static ForexSyncResultDTO ok() {
        return new ForexSyncResultDTO(FrankfurterClient.PROVIDER_NAME, LocalDate.of(2026, 10, 26), List.of(), List.of());
    }

    /**
     * TC-FXSCH-01: tests {@code ForexRateSyncScheduler.syncOnStartup}.
     *
     * <p><b>Pre-condition:</b> Application ready.</p>
     * <p><b>Post-condition:</b> One sync is run.</p>
     */
    @Test
    @DisplayName("TC-FXSCH-01 | ForexRateSyncScheduler.syncOnStartup | application ready -> one sync is run")
    void startup_runsSync() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnStartup();
        verify(forexRateSyncService, times(1)).syncRates();
    }

    /**
     * TC-FXSCH-02: tests {@code ForexRateSyncScheduler.syncOnStartup}.
     *
     * <p><b>Pre-condition:</b> Provider down.</p>
     * <p><b>Post-condition:</b> Startup continues, no exception.</p>
     */
    @Test
    @DisplayName("TC-FXSCH-02 | ForexRateSyncScheduler.syncOnStartup | provider down -> startup continues, no exception")
    void startup_providerDown() {
        when(forexRateSyncService.syncRates()).thenThrow(new ForexProviderUnavailableException("down"));
        assertDoesNotThrow(() -> scheduler.syncOnStartup());
    }

    /**
     * TC-FXSCH-03: tests {@code ForexRateSyncScheduler.syncOnStartup}.
     *
     * <p><b>Pre-condition:</b> Unexpected bug.</p>
     * <p><b>Post-condition:</b> Not swallowed (propagates).</p>
     */
    @Test
    @DisplayName("TC-FXSCH-03 | ForexRateSyncScheduler.syncOnStartup | unexpected bug -> not swallowed (propagates)")
    void startup_unexpectedError() {
        when(forexRateSyncService.syncRates()).thenThrow(new IllegalStateException("bug"));
        assertThrows(IllegalStateException.class, () -> scheduler.syncOnStartup());
    }

    /**
     * TC-FXSCH-04: tests {@code ForexRateSyncScheduler.syncOnSchedule}.
     *
     * <p><b>Pre-condition:</b> Cron fires.</p>
     * <p><b>Post-condition:</b> One sync is run.</p>
     */
    @Test
    @DisplayName("TC-FXSCH-04 | ForexRateSyncScheduler.syncOnSchedule | cron fires -> one sync is run")
    void schedule_runsSync() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnSchedule();
        verify(forexRateSyncService, times(1)).syncRates();
    }

    /**
     * TC-FXSCH-05: tests {@code ForexRateSyncScheduler.syncOnSchedule}.
     *
     * <p><b>Pre-condition:</b> Provider down.</p>
     * <p><b>Post-condition:</b> Logged, scheduler keeps running.</p>
     */
    @Test
    @DisplayName("TC-FXSCH-05 | ForexRateSyncScheduler.syncOnSchedule | provider down -> logged, scheduler keeps running")
    void schedule_providerDown() {
        when(forexRateSyncService.syncRates()).thenThrow(new ForexProviderUnavailableException("down"));
        assertDoesNotThrow(() -> scheduler.syncOnSchedule());
        verify(forexRateSyncService).syncRates();
    }

    /**
     * TC-FXSCH-06: tests {@code ForexRateSyncScheduler.syncOnSchedule}.
     *
     * <p><b>Pre-condition:</b> Fires twice.</p>
     * <p><b>Post-condition:</b> Two independent syncs.</p>
     */
    @Test
    @DisplayName("TC-FXSCH-06 | ForexRateSyncScheduler.syncOnSchedule | fires twice -> two independent syncs")
    void schedule_twice() {
        when(forexRateSyncService.syncRates()).thenReturn(ok());
        scheduler.syncOnSchedule();
        scheduler.syncOnSchedule();
        verify(forexRateSyncService, times(2)).syncRates();
    }
}
