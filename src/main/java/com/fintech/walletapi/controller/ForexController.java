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

@RestController
@Tag(name = "4. Forex", description = "Currencies, exchange rates, live sync and quotes")
@RequestMapping("/api/v1/forex")
public class ForexController {

    private final ExchangeRateService exchangeRateService;
    private final ForexRateSyncService forexRateSyncService;

    public ForexController(ExchangeRateService exchangeRateService, ForexRateSyncService forexRateSyncService) {
        this.exchangeRateService = exchangeRateService;
        this.forexRateSyncService = forexRateSyncService;
    }

    @GetMapping("/currencies")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSupportedCurrencies() {
        Map<String, String> currencies = new LinkedHashMap<>();
        Arrays.stream(SupportedCurrency.values()).forEach(c -> currencies.put(c.name(), c.getDisplayName()));
        return ResponseEntity.ok(new ApiResponse<>(200, "Supported currencies", currencies));
    }

    @GetMapping("/rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateDTO>>> getAllRates() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rates retrieved", exchangeRateService.getAllRates()));
    }

    @GetMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> getRateById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate found", exchangeRateService.getRateById(id)));
    }

    @PostMapping("/rates")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> createRate(@Valid @RequestBody ExchangeRateDTO dto) {
        ExchangeRateDTO saved = exchangeRateService.createRate(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Exchange rate created", saved));
    }

    @PutMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> updateRate(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody ExchangeRateDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate updated", exchangeRateService.updateRate(id, dto)));
    }

    @DeleteMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRate(@PathVariable("id") Long id) {
        exchangeRateService.deleteRate(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rate deleted"));
    }

    @PostMapping("/rates/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ForexSyncResultDTO>> syncRates() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Exchange rates synced", forexRateSyncService.syncRates()));
    }

    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<ForexQuoteDTO>> getQuote(@RequestParam("from") String from,
                                                               @RequestParam("to") String to,
                                                               @RequestParam("amount") BigDecimal amount) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Quote generated", exchangeRateService.getQuote(from, to, amount)));
    }
}
