package com.fintech.walletapi.client;

import com.fintech.walletapi.client.FrankfurterClient.LatestRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link FrankfurterClient} against a mock HTTP server (no network).
 */
class FrankfurterClientTest {

    private static final String BASE_URL = "https://frankfurter.test/v1";
    private static final String USD_PHP_URL = BASE_URL + "/latest?base=USD&symbols=PHP";

    private MockRestServiceServer server;
    private FrankfurterClient client;

    /**
     * Binds a mock HTTP server to a fresh client before each test.
     *
     * <p><b>Pre-condition:</b> A new test is about to run.</p>
     * <p><b>Post-condition:</b> {@code client} sends every request to {@code server} instead of the real
     * Frankfurter API.</p>
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FrankfurterClient(builder, BASE_URL);
    }

    /**
     * TC-FXCLI-01: tests {@code FrankfurterClient.fetchLatestRate}.
     *
     * <p><b>Pre-condition:</b> USD-&gt;PHP 63.944.</p>
     * <p><b>Post-condition:</b> Rate and ECB date parsed.</p>
     */
    @Test
    @DisplayName("TC-FXCLI-01 | FrankfurterClient.fetchLatestRate | USD->PHP 63.944 -> rate and ECB date parsed")
    void fetch_parsesRateAndDate() {
        server.expect(requestTo(USD_PHP_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-10-26\",\"rates\":{\"PHP\":63.944}}",
                        MediaType.APPLICATION_JSON));

        LatestRate latest = client.fetchLatestRate("USD", "PHP");

        assertEquals(0, new BigDecimal("63.944").compareTo(latest.rate()));
        assertEquals(LocalDate.of(2026, 10, 26), latest.date());
        server.verify();
    }

    /**
     * TC-FXCLI-02: tests {@code FrankfurterClient.fetchLatestRate}.
     *
     * <p><b>Pre-condition:</b> API answers 404.</p>
     * <p><b>Post-condition:</b> RestClientException.</p>
     */
    @Test
    @DisplayName("TC-FXCLI-02 | FrankfurterClient.fetchLatestRate | API answers 404 -> RestClientException")
    void fetch_httpError() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }

    /**
     * TC-FXCLI-03: tests {@code FrankfurterClient.fetchLatestRate}.
     *
     * <p><b>Pre-condition:</b> API down (503).</p>
     * <p><b>Post-condition:</b> RestClientException.</p>
     */
    @Test
    @DisplayName("TC-FXCLI-03 | FrankfurterClient.fetchLatestRate | API down (503) -> RestClientException")
    void fetch_serverError() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }

    /**
     * TC-FXCLI-04: tests {@code FrankfurterClient.fetchLatestRate}.
     *
     * <p><b>Pre-condition:</b> Response without the quote currency.</p>
     * <p><b>Post-condition:</b> RestClientException.</p>
     */
    @Test
    @DisplayName("TC-FXCLI-04 | FrankfurterClient.fetchLatestRate | response without the quote currency -> RestClientException")
    void fetch_missingQuote() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withSuccess(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-10-26\",\"rates\":{}}", MediaType.APPLICATION_JSON));
        RestClientException ex = assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
        assertTrue(ex.getMessage().contains("USD to PHP"));
    }

    /**
     * TC-FXCLI-05: tests {@code FrankfurterClient.fetchLatestRate}.
     *
     * <p><b>Pre-condition:</b> Zero rate.</p>
     * <p><b>Post-condition:</b> RestClientException.</p>
     */
    @Test
    @DisplayName("TC-FXCLI-05 | FrankfurterClient.fetchLatestRate | zero rate -> RestClientException")
    void fetch_zeroRate() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withSuccess(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-10-26\",\"rates\":{\"PHP\":0}}", MediaType.APPLICATION_JSON));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }
}
