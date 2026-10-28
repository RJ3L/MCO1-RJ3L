package com.fintech.walletapi.service;

import com.fintech.walletapi.client.FrankfurterClient;
import com.fintech.walletapi.client.FrankfurterClient.LatestRate;
import com.fintech.walletapi.dto.ExchangeRateDTO;
import com.fintech.walletapi.dto.ForexSyncResultDTO;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.SupportedCurrency;
import com.fintech.walletapi.exception.ForexProviderUnavailableException;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Refreshes the exchange-rate table from the live Frankfurter API.
 *
 * <p>For every foreign {@link SupportedCurrency} it fetches the rate into the
 * home currency (e.g. {@code USD -> PHP}) and inserts or updates that row.
 * Reverse and cross pairs keep being derived by {@link ExchangeRateService}.
 * A currency whose fetch fails keeps its current (e.g. seeded) rate.</p>
 *
 * <p>This service is not secured itself because the scheduler calls it without a
 * logged-in user; the HTTP endpoint that exposes it is admin-only.</p>
 */
@Service
public class ForexRateSyncService {

    private static final Logger log = LoggerFactory.getLogger(ForexRateSyncService.class);

    private final FrankfurterClient frankfurterClient;
    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Creates the service.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param frankfurterClient      live-rate API client
     * @param exchangeRateRepository exchange-rate repository
     */
    public ForexRateSyncService(FrankfurterClient frankfurterClient, ExchangeRateRepository exchangeRateRepository) {
        this.frankfurterClient = frankfurterClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Pulls the latest rates and writes them to the exchange-rate table.
     *
     * <p><b>Pre-condition:</b> none.</p>
     * <p><b>Post-condition:</b> every foreign currency the provider answered for has an up-to-date
     * {@code X -> PHP} row (scaled to 6 decimals, HALF_EVEN); all other rows are unchanged.</p>
     *
     * @return the rates that were updated and the currencies that were not
     * @throws ForexProviderUnavailableException if not a single rate could be fetched (503)
     */
    public ForexSyncResultDTO syncRates() {
        // Collect what was refreshed, what failed and the ECB dates received
        String home = SupportedCurrency.HOME.name();
        List<ExchangeRateDTO> updated = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();

        // Fetch each foreign currency into PHP on its own, so one failure never blocks the others
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            if (currency == SupportedCurrency.HOME) {
                continue;
            }
            String base = currency.name();
            try {
                LatestRate latest = frankfurterClient.fetchLatestRate(base, home);
                updated.add(upsert(base, home, latest));
                dates.add(latest.date());
            } catch (RestClientException ex) {
                // Keep the stored (seeded or older) rate for this currency and report it as failed
                log.warn("Could not refresh {} -> {} from Frankfurter: {}", base, home, ex.getMessage());
                failed.add(base);
            }
        }

        // Nothing could be fetched at all: tell the caller (503) instead of returning an empty success
        if (updated.isEmpty()) {
            throw new ForexProviderUnavailableException(
                    "Live exchange rates are unavailable right now; the stored rates were kept");
        }
        // The newest ECB publication date among the rates received
        LocalDate rateDate = dates.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        log.info("Synced {} exchange rate(s) from Frankfurter (rate date {}); failed: {}",
                updated.size(), rateDate, failed);
        return new ForexSyncResultDTO(FrankfurterClient.PROVIDER_NAME, rateDate, updated, failed);
    }

    /**
     * Inserts the pair if it is new, otherwise overwrites its rate.
     *
     * <p><b>Pre-condition:</b> codes are normalized upper-case ISO codes; the rate is positive.
     * <b>Post-condition:</b> exactly one {@code base -> quote} row holds the new rate.</p>
     *
     * @param base   currency converted from
     * @param quote  currency converted to
     * @param latest the fetched rate
     * @return the saved row as a DTO
     */
    private ExchangeRateDTO upsert(String base, String quote, LatestRate latest) {
        ExchangeRate row = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote)
                .orElseGet(() -> new ExchangeRate(base, quote, null));
        row.setRate(latest.rate().setScale(ExchangeRateService.RATE_SCALE, RoundingMode.HALF_EVEN));
        ExchangeRate saved = exchangeRateRepository.save(row);

        ExchangeRateDTO dto = new ExchangeRateDTO();
        dto.setId(saved.getId());
        dto.setBaseCurrency(saved.getBaseCurrency());
        dto.setQuoteCurrency(saved.getQuoteCurrency());
        dto.setRate(saved.getRate());
        dto.setUpdatedAt(saved.getUpdatedAt());
        return dto;
    }
}
