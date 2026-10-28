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

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "gmoney.forex.sync.enabled", havingValue = "true")
public class ForexRateSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ForexRateSyncScheduler.class);

    private final ForexRateSyncService forexRateSyncService;

    public ForexRateSyncScheduler(ForexRateSyncService forexRateSyncService) {
        this.forexRateSyncService = forexRateSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        syncQuietly();
    }

    @Scheduled(cron = "${gmoney.forex.sync.cron:0 30 16 * * MON-FRI}", zone = "Europe/Berlin")
    public void syncOnSchedule() {
        syncQuietly();
    }

    private void syncQuietly() {
        try {
            forexRateSyncService.syncRates();
        } catch (ForexProviderUnavailableException ex) {
            log.warn("Scheduled forex sync skipped: {}", ex.getMessage());
        }
    }
}
