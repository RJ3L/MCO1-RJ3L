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

@Service
public class ForexRateSyncService {

    private static final Logger log = LoggerFactory.getLogger(ForexRateSyncService.class);

    private final FrankfurterClient frankfurterClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public ForexRateSyncService(FrankfurterClient frankfurterClient, ExchangeRateRepository exchangeRateRepository) {
        this.frankfurterClient = frankfurterClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public ForexSyncResultDTO syncRates() {
        String home = SupportedCurrency.HOME.name();
        List<ExchangeRateDTO> updated = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();

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
                log.warn("Could not refresh {} -> {} from Frankfurter: {}", base, home, ex.getMessage());
                failed.add(base);
            }
        }

        if (updated.isEmpty()) {
            throw new ForexProviderUnavailableException(
                    "Live exchange rates are unavailable right now; the stored rates were kept");
        }
        LocalDate rateDate = dates.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        log.info("Synced {} exchange rate(s) from Frankfurter (rate date {}); failed: {}",
                updated.size(), rateDate, failed);
        return new ForexSyncResultDTO(FrankfurterClient.PROVIDER_NAME, rateDate, updated, failed);
    }

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
