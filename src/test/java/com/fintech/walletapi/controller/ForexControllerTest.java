package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.ExchangeRateDTO;
import com.fintech.walletapi.dto.ForexQuoteDTO;
import com.fintech.walletapi.dto.ForexSyncResultDTO;
import com.fintech.walletapi.exception.ForexProviderUnavailableException;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.security.CustomUserDetailsService;
import com.fintech.walletapi.security.JwtUtil;
import com.fintech.walletapi.service.ExchangeRateService;
import com.fintech.walletapi.service.ForexRateSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link ForexController}.
 */
@WebMvcTest(ForexController.class)
@AutoConfigureMockMvc(addFilters = false)
class ForexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private ForexRateSyncService forexRateSyncService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * Builds an exchange-rate DTO.
     *
     * <p><b>Pre-condition:</b> {@code rate} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param id rate id
     * @param base base currency
     * @param quote quote currency
     * @param rate rate as text
     * @return the rate DTO
     */
    private static ExchangeRateDTO dto(Long id, String base, String quote, String rate) {
        ExchangeRateDTO d = new ExchangeRateDTO();
        d.setId(id);
        d.setBaseCurrency(base);
        d.setQuoteCurrency(quote);
        d.setRate(new BigDecimal(rate));
        return d;
    }

    private static final String USD_PHP = "{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"PHP\",\"rate\":58.25}";

    /**
     * TC-FXCTL-01: tests {@code ForexController.getSupportedCurrencies}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> -&gt; 200 with PHP 'Philippine Peso'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-01 | ForexController.getSupportedCurrencies | -> 200 with PHP 'Philippine Peso'")
    void currencies_php() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.PHP").value("Philippine Peso"));
    }

    /**
     * TC-FXCTL-02: tests {@code ForexController.getSupportedCurrencies}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> -&gt; USD present.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-02 | ForexController.getSupportedCurrencies | -> USD present")
    void currencies_usd() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(jsonPath("$.data.USD").value("US Dollar"));
    }

    /**
     * TC-FXCTL-03: tests {@code ForexController.getSupportedCurrencies}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> -&gt; unsupported JPY absent, message set.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-03 | ForexController.getSupportedCurrencies | -> unsupported JPY absent, message set")
    void currencies_noJpy() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(jsonPath("$.data.JPY").doesNotExist())
                .andExpect(jsonPath("$.message").value("Supported currencies"));
    }

    /**
     * TC-FXCTL-04: tests {@code ForexController.getAllRates}.
     *
     * <p><b>Pre-condition:</b> Two rates.</p>
     * <p><b>Post-condition:</b> 200 with both.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-04 | ForexController.getAllRates | two rates -> 200 with both")
    void rates_list() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of(dto(1L, "EUR", "PHP", "63"), dto(2L, "USD", "PHP", "58")));
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].baseCurrency").value("USD"));
    }

    /**
     * TC-FXCTL-05: tests {@code ForexController.getAllRates}.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> 200 with empty list.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-05 | ForexController.getAllRates | none -> 200 with empty list")
    void rates_empty() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * TC-FXCTL-06: tests {@code ForexController.getAllRates}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Message 'Exchange rates retrieved'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-06 | ForexController.getAllRates | message 'Exchange rates retrieved'")
    void rates_message() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(jsonPath("$.message").value("Exchange rates retrieved"));
    }

    /**
     * TC-FXCTL-07: tests {@code ForexController.getRateById}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> 200 with rate.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-07 | ForexController.getRateById | existing id -> 200 with rate")
    void rateById_found() throws Exception {
        when(exchangeRateService.getRateById(1L)).thenReturn(dto(1L, "USD", "PHP", "58.25"));
        mockMvc.perform(get("/api/v1/forex/rates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").value(58.25));
    }

    /**
     * TC-FXCTL-08: tests {@code ForexController.getRateById}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> 404.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-08 | ForexController.getRateById | unknown id -> 404")
    void rateById_missing() throws Exception {
        when(exchangeRateService.getRateById(9L)).thenThrow(new IllegalArgumentException("Exchange rate not found"));
        mockMvc.perform(get("/api/v1/forex/rates/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Exchange rate not found"));
    }

    /**
     * TC-FXCTL-09: tests {@code ForexController.getRateById}.
     *
     * <p><b>Pre-condition:</b> Non-numeric id.</p>
     * <p><b>Post-condition:</b> 400.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-09 | ForexController.getRateById | non-numeric id -> 400")
    void rateById_badId() throws Exception {
        mockMvc.perform(get("/api/v1/forex/rates/usd"))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-FXCTL-10: tests {@code ForexController.createRate}.
     *
     * <p><b>Pre-condition:</b> Valid body.</p>
     * <p><b>Post-condition:</b> 201 'Exchange rate created'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-10 | ForexController.createRate | valid body -> 201 'Exchange rate created'")
    void createRate_ok() throws Exception {
        when(exchangeRateService.createRate(any())).thenReturn(dto(5L, "USD", "PHP", "58.25"));
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    /**
     * TC-FXCTL-11: tests {@code ForexController.createRate}.
     *
     * <p><b>Pre-condition:</b> Negative rate.</p>
     * <p><b>Post-condition:</b> 400 'Rate must be greater than zero'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-11 | ForexController.createRate | negative rate -> 400 'Rate must be greater than zero'")
    void createRate_negative() throws Exception {
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"PHP\",\"rate\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rate must be greater than zero"));
    }

    /**
     * TC-FXCTL-12: tests {@code ForexController.createRate}.
     *
     * <p><b>Pre-condition:</b> Duplicate pair.</p>
     * <p><b>Post-condition:</b> 400 from InvalidTransactionException.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-12 | ForexController.createRate | duplicate pair -> 400 from InvalidTransactionException")
    void createRate_duplicate() throws Exception {
        when(exchangeRateService.createRate(any())).thenThrow(new InvalidTransactionException("already exists"));
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value("Invalid Transaction"));
    }

    /**
     * TC-FXCTL-13: tests {@code ForexController.updateRate}.
     *
     * <p><b>Pre-condition:</b> Valid body.</p>
     * <p><b>Post-condition:</b> 200 'Exchange rate updated'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-13 | ForexController.updateRate | valid body -> 200 'Exchange rate updated'")
    void updateRate_ok() throws Exception {
        when(exchangeRateService.updateRate(eq(1L), any())).thenReturn(dto(1L, "USD", "PHP", "58.25"));
        mockMvc.perform(put("/api/v1/forex/rates/1").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exchange rate updated"));
    }

    /**
     * TC-FXCTL-14: tests {@code ForexController.updateRate}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> 404.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-14 | ForexController.updateRate | unknown id -> 404")
    void updateRate_missing() throws Exception {
        when(exchangeRateService.updateRate(eq(9L), any())).thenThrow(new IllegalArgumentException("Exchange rate not found"));
        mockMvc.perform(put("/api/v1/forex/rates/9").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isNotFound());
    }

    /**
     * TC-FXCTL-15: tests {@code ForexController.updateRate}.
     *
     * <p><b>Pre-condition:</b> Non-admin.</p>
     * <p><b>Post-condition:</b> 403.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-15 | ForexController.updateRate | non-admin -> 403")
    void updateRate_forbidden() throws Exception {
        when(exchangeRateService.updateRate(eq(1L), any())).thenThrow(new AccessDeniedException("admin only"));
        mockMvc.perform(put("/api/v1/forex/rates/1").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isForbidden());
    }

    /**
     * TC-FXCTL-16: tests {@code ForexController.deleteRate}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> 200 'Exchange rate deleted'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-16 | ForexController.deleteRate | existing id -> 200 'Exchange rate deleted'")
    void deleteRate_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/forex/rates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exchange rate deleted"));
        verify(exchangeRateService).deleteRate(1L);
    }

    /**
     * TC-FXCTL-17: tests {@code ForexController.deleteRate}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> 404.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-17 | ForexController.deleteRate | unknown id -> 404")
    void deleteRate_missing() throws Exception {
        doThrow(new IllegalArgumentException("Exchange rate not found")).when(exchangeRateService).deleteRate(9L);
        mockMvc.perform(delete("/api/v1/forex/rates/9"))
                .andExpect(status().isNotFound());
    }

    /**
     * TC-FXCTL-18: tests {@code ForexController.deleteRate}.
     *
     * <p><b>Pre-condition:</b> Non-admin.</p>
     * <p><b>Post-condition:</b> 403.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-18 | ForexController.deleteRate | non-admin -> 403")
    void deleteRate_forbidden() throws Exception {
        doThrow(new AccessDeniedException("admin only")).when(exchangeRateService).deleteRate(1L);
        mockMvc.perform(delete("/api/v1/forex/rates/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * TC-FXCTL-19: tests {@code ForexController.getQuote}.
     *
     * <p><b>Pre-condition:</b> 1000 PHP.</p>
     * <p><b>Post-condition:</b> USD -&gt; 200 with converted amount.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-19 | ForexController.getQuote | 1000 PHP -> USD -> 200 with converted amount")
    void quote_ok() throws Exception {
        when(exchangeRateService.getQuote("PHP", "USD", new BigDecimal("1000")))
                .thenReturn(new ForexQuoteDTO("PHP", "USD", new BigDecimal("1000"),
                        new BigDecimal("0.017241"), new BigDecimal("17.24")));
        mockMvc.perform(get("/api/v1/forex/quote").param("from", "PHP").param("to", "USD").param("amount", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedAmount").value(17.24));
    }

    /**
     * TC-FXCTL-20: tests {@code ForexController.getQuote}.
     *
     * <p><b>Pre-condition:</b> Missing 'to' parameter.</p>
     * <p><b>Post-condition:</b> 400.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-20 | ForexController.getQuote | missing 'to' parameter -> 400")
    void quote_missingParam() throws Exception {
        mockMvc.perform(get("/api/v1/forex/quote").param("from", "PHP").param("amount", "1000"))
                .andExpect(status().isBadRequest());
    }

    /**
     * TC-FXCTL-21: tests {@code ForexController.getQuote}.
     *
     * <p><b>Pre-condition:</b> No rate for pair.</p>
     * <p><b>Post-condition:</b> 404.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-FXCTL-21 | ForexController.getQuote | no rate for pair -> 404")
    void quote_noRate() throws Exception {
        when(exchangeRateService.getQuote(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Exchange rate not found for GBP to SGD"));
        mockMvc.perform(get("/api/v1/forex/quote").param("from", "GBP").param("to", "SGD").param("amount", "5"))
                .andExpect(status().isNotFound());
    }

    /**
     * TC-FXCTL-22: tests {@code ForexController.syncRates}.
     *
     * <p><b>Pre-condition:</b> Admin.</p>
     * <p><b>Post-condition:</b> 200 with live rates and provider.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FXCTL-22 | ForexController.syncRates | admin -> 200 with live rates and provider")
    void sync_admin() throws Exception {
        when(forexRateSyncService.syncRates()).thenReturn(new ForexSyncResultDTO("Frankfurter",
                LocalDate.of(2026, 10, 26), List.of(dto(1L, "USD", "PHP", "63.944000")), List.of()));
        mockMvc.perform(post("/api/v1/forex/rates/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exchange rates synced"))
                .andExpect(jsonPath("$.data.provider").value("Frankfurter"))
                .andExpect(jsonPath("$.data.updatedRates[0].rate").value(63.944));
    }

    /**
     * TC-FXCTL-23: tests {@code ForexController.syncRates}.
     *
     * <p><b>Pre-condition:</b> Provider unreachable.</p>
     * <p><b>Post-condition:</b> 503.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FXCTL-23 | ForexController.syncRates | provider unreachable -> 503")
    void sync_providerDown() throws Exception {
        when(forexRateSyncService.syncRates())
                .thenThrow(new ForexProviderUnavailableException("Live exchange rates are unavailable right now"));
        mockMvc.perform(post("/api/v1/forex/rates/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorType").value("Service Unavailable"));
    }

    /**
     * TC-FXCTL-24: tests {@code ForexController.syncRates}.
     *
     * <p><b>Pre-condition:</b> GBP not refreshed.</p>
     * <p><b>Post-condition:</b> 200 listing GBP in failedCurrencies.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FXCTL-24 | ForexController.syncRates | GBP not refreshed -> 200 listing GBP in failedCurrencies")
    void sync_partialFailure() throws Exception {
        when(forexRateSyncService.syncRates()).thenReturn(new ForexSyncResultDTO("Frankfurter",
                LocalDate.of(2026, 10, 26), List.of(dto(1L, "USD", "PHP", "63.944000")), List.of("GBP")));
        mockMvc.perform(post("/api/v1/forex/rates/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCurrencies[0]").value("GBP"))
                .andExpect(jsonPath("$.data.rateDate").value("2026-10-26"));
    }
}
