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

    private static ExchangeRateDTO dto(Long id, String base, String quote, String rate) {
        ExchangeRateDTO d = new ExchangeRateDTO();
        d.setId(id);
        d.setBaseCurrency(base);
        d.setQuoteCurrency(quote);
        d.setRate(new BigDecimal(rate));
        return d;
    }

    private static final String USD_PHP = "{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"PHP\",\"rate\":58.25}";

    @Test
    @DisplayName("TC-FXCTL-01 | ForexController.getSupportedCurrencies | -> 200 with PHP 'Philippine Peso'")
    void currencies_php() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.PHP").value("Philippine Peso"));
    }

    @Test
    @DisplayName("TC-FXCTL-02 | ForexController.getSupportedCurrencies | -> USD present")
    void currencies_usd() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(jsonPath("$.data.USD").value("US Dollar"));
    }

    @Test
    @DisplayName("TC-FXCTL-03 | ForexController.getSupportedCurrencies | -> unsupported JPY absent, message set")
    void currencies_noJpy() throws Exception {
        mockMvc.perform(get("/api/v1/forex/currencies"))
                .andExpect(jsonPath("$.data.JPY").doesNotExist())
                .andExpect(jsonPath("$.message").value("Supported currencies"));
    }

    @Test
    @DisplayName("TC-FXCTL-04 | ForexController.getAllRates | two rates -> 200 with both")
    void rates_list() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of(dto(1L, "EUR", "PHP", "63"), dto(2L, "USD", "PHP", "58")));
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].baseCurrency").value("USD"));
    }

    @Test
    @DisplayName("TC-FXCTL-05 | ForexController.getAllRates | none -> 200 with empty list")
    void rates_empty() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("TC-FXCTL-06 | ForexController.getAllRates | message 'Exchange rates retrieved'")
    void rates_message() throws Exception {
        when(exchangeRateService.getAllRates()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/forex/rates"))
                .andExpect(jsonPath("$.message").value("Exchange rates retrieved"));
    }

    @Test
    @DisplayName("TC-FXCTL-07 | ForexController.getRateById | existing id -> 200 with rate")
    void rateById_found() throws Exception {
        when(exchangeRateService.getRateById(1L)).thenReturn(dto(1L, "USD", "PHP", "58.25"));
        mockMvc.perform(get("/api/v1/forex/rates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").value(58.25));
    }

    @Test
    @DisplayName("TC-FXCTL-08 | ForexController.getRateById | unknown id -> 404")
    void rateById_missing() throws Exception {
        when(exchangeRateService.getRateById(9L)).thenThrow(new IllegalArgumentException("Exchange rate not found"));
        mockMvc.perform(get("/api/v1/forex/rates/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Exchange rate not found"));
    }

    @Test
    @DisplayName("TC-FXCTL-09 | ForexController.getRateById | non-numeric id -> 400")
    void rateById_badId() throws Exception {
        mockMvc.perform(get("/api/v1/forex/rates/usd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-FXCTL-10 | ForexController.createRate | valid body -> 201 'Exchange rate created'")
    void createRate_ok() throws Exception {
        when(exchangeRateService.createRate(any())).thenReturn(dto(5L, "USD", "PHP", "58.25"));
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("TC-FXCTL-11 | ForexController.createRate | negative rate -> 400 'Rate must be greater than zero'")
    void createRate_negative() throws Exception {
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"PHP\",\"rate\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rate must be greater than zero"));
    }

    @Test
    @DisplayName("TC-FXCTL-12 | ForexController.createRate | duplicate pair -> 400 from InvalidTransactionException")
    void createRate_duplicate() throws Exception {
        when(exchangeRateService.createRate(any())).thenThrow(new InvalidTransactionException("already exists"));
        mockMvc.perform(post("/api/v1/forex/rates").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value("Invalid Transaction"));
    }

    @Test
    @DisplayName("TC-FXCTL-13 | ForexController.updateRate | valid body -> 200 'Exchange rate updated'")
    void updateRate_ok() throws Exception {
        when(exchangeRateService.updateRate(eq(1L), any())).thenReturn(dto(1L, "USD", "PHP", "58.25"));
        mockMvc.perform(put("/api/v1/forex/rates/1").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exchange rate updated"));
    }

    @Test
    @DisplayName("TC-FXCTL-14 | ForexController.updateRate | unknown id -> 404")
    void updateRate_missing() throws Exception {
        when(exchangeRateService.updateRate(eq(9L), any())).thenThrow(new IllegalArgumentException("Exchange rate not found"));
        mockMvc.perform(put("/api/v1/forex/rates/9").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-FXCTL-15 | ForexController.updateRate | non-admin -> 403")
    void updateRate_forbidden() throws Exception {
        when(exchangeRateService.updateRate(eq(1L), any())).thenThrow(new AccessDeniedException("admin only"));
        mockMvc.perform(put("/api/v1/forex/rates/1").contentType(MediaType.APPLICATION_JSON).content(USD_PHP))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-FXCTL-16 | ForexController.deleteRate | existing id -> 200 'Exchange rate deleted'")
    void deleteRate_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/forex/rates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exchange rate deleted"));
        verify(exchangeRateService).deleteRate(1L);
    }

    @Test
    @DisplayName("TC-FXCTL-17 | ForexController.deleteRate | unknown id -> 404")
    void deleteRate_missing() throws Exception {
        doThrow(new IllegalArgumentException("Exchange rate not found")).when(exchangeRateService).deleteRate(9L);
        mockMvc.perform(delete("/api/v1/forex/rates/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-FXCTL-18 | ForexController.deleteRate | non-admin -> 403")
    void deleteRate_forbidden() throws Exception {
        doThrow(new AccessDeniedException("admin only")).when(exchangeRateService).deleteRate(1L);
        mockMvc.perform(delete("/api/v1/forex/rates/1"))
                .andExpect(status().isForbidden());
    }

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

    @Test
    @DisplayName("TC-FXCTL-20 | ForexController.getQuote | missing 'to' parameter -> 400")
    void quote_missingParam() throws Exception {
        mockMvc.perform(get("/api/v1/forex/quote").param("from", "PHP").param("amount", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-FXCTL-21 | ForexController.getQuote | no rate for pair -> 404")
    void quote_noRate() throws Exception {
        when(exchangeRateService.getQuote(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Exchange rate not found for GBP to SGD"));
        mockMvc.perform(get("/api/v1/forex/quote").param("from", "GBP").param("to", "SGD").param("amount", "5"))
                .andExpect(status().isNotFound());
    }

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
