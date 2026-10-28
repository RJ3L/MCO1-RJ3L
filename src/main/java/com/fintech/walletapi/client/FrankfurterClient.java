package com.fintech.walletapi.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

@Component
public class FrankfurterClient {

    public static final String PROVIDER_NAME = "Frankfurter (European Central Bank reference rates)";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    @Autowired
    public FrankfurterClient(@Value("${gmoney.forex.frankfurter.base-url:https://api.frankfurter.dev/v1}") String baseUrl) {
        this(RestClient.builder().requestFactory(timeoutRequestFactory()), baseUrl);
    }

    public FrankfurterClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }

    public LatestRate fetchLatestRate(String base, String quote) {
        FrankfurterResponse response = restClient.get()
                .uri(uri -> uri.path("/latest")
                        .queryParam("base", base)
                        .queryParam("symbols", quote)
                        .build())
                .retrieve()
                .body(FrankfurterResponse.class);

        BigDecimal rate = response == null || response.rates() == null ? null : response.rates().get(quote);
        if (rate == null || rate.signum() <= 0) {
            throw new RestClientException("Frankfurter returned no rate for " + base + " to " + quote);
        }
        return new LatestRate(rate, response.date());
    }

    public record LatestRate(BigDecimal rate, LocalDate date) {
    }

    record FrankfurterResponse(BigDecimal amount, String base, LocalDate date, Map<String, BigDecimal> rates) {
    }
}
