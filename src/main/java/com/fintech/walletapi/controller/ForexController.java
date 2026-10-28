package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.ApiResponse;
import com.fintech.walletapi.dto.ExchangeRateDTO;
import com.fintech.walletapi.dto.ForexQuoteDTO;
import com.fintech.walletapi.dto.ForexSyncResultDTO;
import com.fintech.walletapi.entity.SupportedCurrency;
import com.fintech.walletapi.service.ExchangeRateService;
import com.fintech.walletapi.service.ForexRateSyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forex endpoints ({@code /api/v1/forex}): supported currencies, exchange-rate
 * CRUD (write operations are admin-only) and conversion quotes.
 */
@RestController
@Tag(name = "4. Forex", description = "Currencies, exchange rates, live sync and quotes")
@RequestMapping("/api/v1/forex")
public class ForexController {

    private final ExchangeRateService exchangeRateService;
    private final ForexRateSyncService forexRateSyncService;

    /**
     * Creates the controller.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param exchangeRateService  forex engine
     * @param forexRateSyncService live-rate sync from Frankfurter
     */
    public ForexController(ExchangeRateService exchangeRateService, ForexRateSyncService forexRateSyncService) {
        this.exchangeRateService = exchangeRateService;
        this.forexRateSyncService = forexRateSyncService;
    }

    /**
     * {@code GET /api/v1/forex/currencies} — the currencies a wallet can hold.
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @return 200 with a map of code to display name, e.g. {@code {"PHP": "Philippine Peso", ...}}
     */
    @GetMapping("/currencies")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSupportedCurrencies() {
        Map<String, String> currencies = new LinkedHashMap<>();
        Arrays.stream(SupportedCurrency.values()).forEach(c -> currencies.put(c.name(), c.getDisplayName()));
        return ResponseEntity.ok(new ApiResponse<>(200, "Supported currencies", currencies));
    }

    /**
     * {@code GET /api/v1/forex/rates} — all stored rates.
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @return 200 with the rates
     */
    @GetMapping("/rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateDTO>>> getAllRates() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rates retrieved", exchangeRateService.getAllRates()));
    }

    /**
     * {@code GET /api/v1/forex/rates/{id}} — one stored rate.
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param id the rate id
     * @return 200 with the rate; 404 if missing
     */
    @GetMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> getRateById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate found", exchangeRateService.getRateById(id)));
    }

    /**
     * {@code POST /api/v1/forex/rates} — adds a rate (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; body passes validation.</p>
     * <p><b>Post-condition:</b> a new rate exists.</p>
     *
     * @param dto base currency, quote currency and rate
     * @return 201 with the saved rate; 400 if invalid or duplicate; 403 for non-admins
     */
    @PostMapping("/rates")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> createRate(@Valid @RequestBody ExchangeRateDTO dto) {
        ExchangeRateDTO saved = exchangeRateService.createRate(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Exchange rate created", saved));
    }

    /**
     * {@code PUT /api/v1/forex/rates/{id}} — changes a rate's value (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; body passes validation.</p>
     * <p><b>Post-condition:</b> the stored rate equals the new value.</p>
     *
     * @param id  the rate id
     * @param dto carries the new rate (currency fields must be present but are not changed)
     * @return 200 with the updated rate; 404 if missing; 403 for non-admins
     */
    @PutMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> updateRate(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody ExchangeRateDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate updated", exchangeRateService.updateRate(id, dto)));
    }

    /**
     * {@code DELETE /api/v1/forex/rates/{id}} — removes a rate (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> the rate no longer exists.</p>
     *
     * @param id the rate id
     * @return 200 when deleted; 404 if missing; 403 for non-admins
     */
    @DeleteMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRate(@PathVariable("id") Long id) {
        exchangeRateService.deleteRate(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate deleted"));
    }

    /**
     * {@code POST /api/v1/forex/rates/sync} — refreshes the rates from the live Frankfurter API (admin).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> every {@code X -> PHP} rate the provider answered for is up to date.</p>
     *
     * @return 200 with the updated rates and any currencies that failed; 503 if none could be fetched;
     *         403 for non-admins
     */
    @PostMapping("/rates/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ForexSyncResultDTO>> syncRates() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rates synced", forexRateSyncService.syncRates()));
    }

    /**
     * {@code GET /api/v1/forex/quote?from=PHP&to=USD&amount=1000} — previews a conversion.
     *
     * <p><b>Pre-condition:</b> a valid JWT; {@code amount} &gt; 0.</p>
     * <p><b>Post-condition:</b> no data is changed (no money moves).</p>
     *
     * @param from   source currency code
     * @param to     target currency code
     * @param amount amount in the source currency
     * @return 200 with the quote; 400 for bad input; 404 if no rate exists
     */
    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<ForexQuoteDTO>> getQuote(@RequestParam("from") String from,
                                                               @RequestParam("to") String to,
                                                               @RequestParam("amount") BigDecimal amount) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Quote generated", exchangeRateService.getQuote(from, to, amount)));
    }
}
