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

class FrankfurterClientTest {

    private static final String BASE_URL = "https://frankfurter.test/v1";
    private static final String USD_PHP_URL = BASE_URL + "/latest?base=USD&symbols=PHP";

    private MockRestServiceServer server;
    private FrankfurterClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FrankfurterClient(builder, BASE_URL);
    }

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

    @Test
    @DisplayName("TC-FXCLI-02 | FrankfurterClient.fetchLatestRate | API answers 404 -> RestClientException")
    void fetch_httpError() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }

    @Test
    @DisplayName("TC-FXCLI-03 | FrankfurterClient.fetchLatestRate | API down (503) -> RestClientException")
    void fetch_serverError() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }

    @Test
    @DisplayName("TC-FXCLI-04 | FrankfurterClient.fetchLatestRate | response without the quote currency -> RestClientException")
    void fetch_missingQuote() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withSuccess(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-10-26\",\"rates\":{}}", MediaType.APPLICATION_JSON));
        RestClientException ex = assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
        assertTrue(ex.getMessage().contains("USD to PHP"));
    }

    @Test
    @DisplayName("TC-FXCLI-05 | FrankfurterClient.fetchLatestRate | zero rate -> RestClientException")
    void fetch_zeroRate() {
        server.expect(requestTo(USD_PHP_URL)).andRespond(withSuccess(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-10-26\",\"rates\":{\"PHP\":0}}", MediaType.APPLICATION_JSON));
        assertThrows(RestClientException.class, () -> client.fetchLatestRate("USD", "PHP"));
    }
}
