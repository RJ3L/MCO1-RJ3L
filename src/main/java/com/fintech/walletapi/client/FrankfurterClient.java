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

/**
 * HTTP client for the <a href="https://frankfurter.dev">Frankfurter</a> open API,
 * a free, key-less service that publishes the European Central Bank's daily
 * reference rates (updated once per working day, around 16:00 CET).
 *
 * <p>Example call: {@code GET /latest?base=USD&symbols=PHP} &rarr;
 * {@code {"amount":1.0,"base":"USD","date":"2026-10-26","rates":{"PHP":63.944}}}</p>
 */
@Component
public class FrankfurterClient {

    /** Name shown in sync results and logs. */
    public static final String PROVIDER_NAME = "Frankfurter (European Central Bank reference rates)";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    /**
     * Creates the client used by the application.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param baseUrl {@code gmoney.forex.frankfurter.base-url}, e.g. {@code https://api.frankfurter.dev/v1}
     */
    @Autowired
    public FrankfurterClient(@Value("${gmoney.forex.frankfurter.base-url:https://api.frankfurter.dev/v1}") String baseUrl) {
        this(RestClient.builder().requestFactory(timeoutRequestFactory()), baseUrl);
    }

    /**
     * Creates the client from a given builder (tests bind a mock server to it).
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param builder REST client builder
     * @param baseUrl the API root
     */
    public FrankfurterClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Configures bounded connection and read timeouts for provider requests.
     * <p><b>Pre-condition:</b> TIMEOUT is positive. <b>Post-condition:</b> a new configured factory is returned.</p>
     * @return a factory with both timeouts set to TIMEOUT
     */
    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }

    /**
     * Fetches the latest rate that converts 1 unit of {@code base} into {@code quote}.
     *
     * <p><b>Pre-condition:</b> both codes are upper-case ISO 4217 codes published by the ECB.</p>
     * <p><b>Post-condition:</b> no local data is changed.</p>
     *
     * @param base  currency converted from, e.g. {@code USD}
     * @param quote currency converted to, e.g. {@code PHP}
     * @return the rate and the date it was published
     * @throws RestClientException if the API cannot be reached, answers with an error,
     *                             or the response has no positive rate for {@code quote}
     */
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

    /**
     * One rate as published by Frankfurter.
     *
     * @param rate units of quote currency per 1 unit of base currency
     * @param date the ECB publication date
     */
    public record LatestRate(BigDecimal rate, LocalDate date) {
    }

    /**
     * JSON body of {@code GET /latest}.
     *
     * @param amount amount converted (always 1 here)
     * @param base   base currency
     * @param date   publication date
     * @param rates  quote currency to rate
     */
    record FrankfurterResponse(BigDecimal amount, String base, LocalDate date, Map<String, BigDecimal> rates) {
    }
}
