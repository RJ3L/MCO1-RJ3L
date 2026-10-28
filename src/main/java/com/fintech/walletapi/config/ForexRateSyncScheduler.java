package com.fintech.walletapi.config;

import com.fintech.walletapi.exception.ForexProviderUnavailableException;
import com.fintech.walletapi.service.ForexRateSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps the exchange-rate table in step with the live Frankfurter rates:
 * once when the application is ready (after {@link DataSeeder} has run, so live
 * rates replace the starter values) and then on a cron schedule.
 *
 * <p>Runs only when {@code gmoney.forex.sync.enabled=true}. The default cron
 * ({@code gmoney.forex.sync.cron}) fires on weekdays at 16:30 Frankfurt time,
 * shortly after the ECB publishes. A failed sync is logged and the stored
 * rates stay in use.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "gmoney.forex.sync.enabled", havingValue = "true")
public class ForexRateSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ForexRateSyncScheduler.class);

    private final ForexRateSyncService forexRateSyncService;

    /**
     * Creates the scheduler.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param forexRateSyncService does the actual sync
     */
    public ForexRateSyncScheduler(ForexRateSyncService forexRateSyncService) {
        this.forexRateSyncService = forexRateSyncService;
    }

    /**
     * Syncs once at startup.
     *
     * <p><b>Pre-condition:</b> the application context is fully started.</p>
     * <p><b>Post-condition:</b> rates are refreshed, or the stored rates are kept if the provider is down.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        syncQuietly();
    }

    /**
     * Syncs on the configured schedule.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> same as {@link #syncOnStartup()}.</p>
     */
    @Scheduled(cron = "${gmoney.forex.sync.cron:0 30 16 * * MON-FRI}", zone = "Europe/Berlin")
    public void syncOnSchedule() {
        syncQuietly();
    }

    /**
     * Runs synchronization and logs a complete provider outage.
     * <p><b>Pre-condition:</b> the sync service is initialized.</p>
     * <p><b>Post-condition:</b> successful currencies are refreshed, or a provider failure is logged.</p>
     */
    private void syncQuietly() {
        try {
            forexRateSyncService.syncRates();
        } catch (ForexProviderUnavailableException ex) {
            log.warn("Scheduled forex sync skipped: {}", ex.getMessage());
        }
    }
}
