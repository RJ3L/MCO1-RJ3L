package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.ExchangeRateDTO;
import com.fintech.walletapi.dto.ForexQuoteDTO;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExchangeRateService}: rate CRUD, lookup (direct, inverse,
 * cross through PHP), conversion and quotes.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    /**
     * Builds a stored exchange-rate entity.
     *
     * <p><b>Pre-condition:</b> {@code value} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param id rate id
     * @param base base currency
     * @param quote quote currency
     * @param value rate as text
     * @return the entity
     */
    private static ExchangeRate rate(Long id, String base, String quote, String value) {
        ExchangeRate r = new ExchangeRate(base, quote, new BigDecimal(value));
        r.setId(id);
        return r;
    }

    /**
     * Builds an exchange-rate request DTO.
     *
     * <p><b>Pre-condition:</b> {@code value} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param base base currency
     * @param quote quote currency
     * @param value rate as text
     * @return the DTO
     */
    private static ExchangeRateDTO rateDto(String base, String quote, String value) {
        ExchangeRateDTO dto = new ExchangeRateDTO();
        dto.setBaseCurrency(base);
        dto.setQuoteCurrency(quote);
        dto.setRate(new BigDecimal(value));
        return dto;
    }

    /**
     * Makes the mocked repository return a stored rate for one direction of a pair.
     *
     * <p><b>Pre-condition:</b> The repository mock is initialised.</p>
     * <p><b>Post-condition:</b> Looking up {@code base -> quote} returns that rate.</p>
     *
     * @param base base currency
     * @param quote quote currency
     * @param value rate as text
     */
    private void stubPair(String base, String quote, String value) {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote))
                .thenReturn(Optional.of(rate(1L, base, quote, value)));
    }

    /**
     * TC-FXSVC-01: tests {@code ExchangeRateService.createRate}.
     *
     * <p><b>Pre-condition:</b> New USD-&gt;PHP 58.25.</p>
     * <p><b>Post-condition:</b> Saved, scaled to 6 dp.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-01 | ExchangeRateService.createRate | new USD->PHP 58.25 -> saved, scaled to 6 dp")
    void createRate_saves() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("USD", "PHP")).thenReturn(false);
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeRateDTO saved = exchangeRateService.createRate(rateDto("usd", "php", "58.25"));

        assertEquals("USD", saved.getBaseCurrency());
        assertEquals("PHP", saved.getQuoteCurrency());
        assertEquals(new BigDecimal("58.250000"), saved.getRate());
    }

    /**
     * TC-FXSVC-02: tests {@code ExchangeRateService.createRate}.
     *
     * <p><b>Pre-condition:</b> Pair already exists.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-02 | ExchangeRateService.createRate | pair already exists -> InvalidTransactionException")
    void createRate_duplicate() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("USD", "PHP")).thenReturn(true);

        assertThrows(InvalidTransactionException.class,
                () -> exchangeRateService.createRate(rateDto("USD", "PHP", "58")));
        verify(exchangeRateRepository, never()).save(any());
    }

    /**
     * TC-FXSVC-03: tests {@code ExchangeRateService.createRate}.
     *
     * <p><b>Pre-condition:</b> Same base and quote (PHP-&gt;PHP).</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-03 | ExchangeRateService.createRate | same base and quote (PHP->PHP) -> InvalidTransactionException")
    void createRate_sameCurrency() {
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> exchangeRateService.createRate(rateDto("PHP", "PHP", "1")));
        assertEquals("Base and quote currency must be different", ex.getMessage());
    }

    /**
     * TC-FXSVC-04: tests {@code ExchangeRateService.createRate}.
     *
     * <p><b>Pre-condition:</b> Unsupported currency JPY.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-04 | ExchangeRateService.createRate | unsupported currency JPY -> InvalidTransactionException")
    void createRate_unsupported() {
        assertThrows(InvalidTransactionException.class,
                () -> exchangeRateService.createRate(rateDto("JPY", "PHP", "0.39")));
    }

    /**
     * TC-FXSVC-05: tests {@code ExchangeRateService.getAllRates}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Rates returned sorted by base currency.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-05 | ExchangeRateService.getAllRates | rates returned sorted by base currency")
    void getAllRates_sorted() {
        when(exchangeRateRepository.findAll()).thenReturn(List.of(
                rate(1L, "USD", "PHP", "58"), rate(2L, "EUR", "PHP", "63")));

        List<ExchangeRateDTO> result = exchangeRateService.getAllRates();

        assertEquals("EUR", result.get(0).getBaseCurrency());
        assertEquals("USD", result.get(1).getBaseCurrency());
    }

    /**
     * TC-FXSVC-06: tests {@code ExchangeRateService.getAllRates}.
     *
     * <p><b>Pre-condition:</b> Empty table.</p>
     * <p><b>Post-condition:</b> Empty list.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-06 | ExchangeRateService.getAllRates | empty table -> empty list")
    void getAllRates_empty() {
        when(exchangeRateRepository.findAll()).thenReturn(List.of());
        assertTrue(exchangeRateService.getAllRates().isEmpty());
    }

    /**
     * TC-FXSVC-07: tests {@code ExchangeRateService.getAllRates}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> DTO keeps id and rate value.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-07 | ExchangeRateService.getAllRates | DTO keeps id and rate value")
    void getAllRates_mapsFields() {
        when(exchangeRateRepository.findAll()).thenReturn(List.of(rate(9L, "SGD", "PHP", "43.5")));
        ExchangeRateDTO dto = exchangeRateService.getAllRates().get(0);
        assertEquals(9L, dto.getId());
        assertEquals(0, new BigDecimal("43.5").compareTo(dto.getRate()));
    }

    /**
     * TC-FXSVC-08: tests {@code ExchangeRateService.getRateById}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> DTO.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-08 | ExchangeRateService.getRateById | existing id -> DTO")
    void getRateById_found() {
        when(exchangeRateRepository.findById(1L)).thenReturn(Optional.of(rate(1L, "USD", "PHP", "58")));
        assertEquals("USD", exchangeRateService.getRateById(1L).getBaseCurrency());
    }

    /**
     * TC-FXSVC-09: tests {@code ExchangeRateService.getRateById}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException 'Exchange rate not found'.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-09 | ExchangeRateService.getRateById | unknown id -> IllegalArgumentException 'Exchange rate not found'")
    void getRateById_missing() {
        when(exchangeRateRepository.findById(99L)).thenReturn(Optional.empty());
        Exception ex = assertThrows(IllegalArgumentException.class, () -> exchangeRateService.getRateById(99L));
        assertEquals("Exchange rate not found", ex.getMessage());
    }

    /**
     * TC-FXSVC-10: tests {@code ExchangeRateService.getRateById}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Quote currency mapped correctly.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-10 | ExchangeRateService.getRateById | quote currency mapped correctly")
    void getRateById_quoteMapped() {
        when(exchangeRateRepository.findById(2L)).thenReturn(Optional.of(rate(2L, "EUR", "USD", "1.08")));
        assertEquals("USD", exchangeRateService.getRateById(2L).getQuoteCurrency());
    }

    /**
     * TC-FXSVC-11: tests {@code ExchangeRateService.updateRate}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> New value stored.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-11 | ExchangeRateService.updateRate | existing id -> new value stored")
    void updateRate_changesValue() {
        when(exchangeRateRepository.findById(1L)).thenReturn(Optional.of(rate(1L, "USD", "PHP", "58")));
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeRateDTO result = exchangeRateService.updateRate(1L, rateDto("USD", "PHP", "59.1"));

        assertEquals(new BigDecimal("59.100000"), result.getRate());
    }

    /**
     * TC-FXSVC-12: tests {@code ExchangeRateService.updateRate}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException, nothing saved.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-12 | ExchangeRateService.updateRate | unknown id -> IllegalArgumentException, nothing saved")
    void updateRate_missing() {
        when(exchangeRateRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> exchangeRateService.updateRate(5L, rateDto("USD", "PHP", "1")));
        verify(exchangeRateRepository, never()).save(any());
    }

    /**
     * TC-FXSVC-13: tests {@code ExchangeRateService.updateRate}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Currency pair in body is ignored (pair cannot change).</p>
     */
    @Test
    @DisplayName("TC-FXSVC-13 | ExchangeRateService.updateRate | currency pair in body is ignored (pair cannot change)")
    void updateRate_keepsPair() {
        when(exchangeRateRepository.findById(1L)).thenReturn(Optional.of(rate(1L, "USD", "PHP", "58")));
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeRateDTO result = exchangeRateService.updateRate(1L, rateDto("EUR", "GBP", "60"));

        assertEquals("USD", result.getBaseCurrency());
        assertEquals("PHP", result.getQuoteCurrency());
    }

    /**
     * TC-FXSVC-14: tests {@code ExchangeRateService.deleteRate}.
     *
     * <p><b>Pre-condition:</b> Existing id.</p>
     * <p><b>Post-condition:</b> Deleted.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-14 | ExchangeRateService.deleteRate | existing id -> deleted")
    void deleteRate_existing() {
        when(exchangeRateRepository.existsById(1L)).thenReturn(true);
        exchangeRateService.deleteRate(1L);
        verify(exchangeRateRepository).deleteById(1L);
    }

    /**
     * TC-FXSVC-15: tests {@code ExchangeRateService.deleteRate}.
     *
     * <p><b>Pre-condition:</b> Unknown id.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-15 | ExchangeRateService.deleteRate | unknown id -> IllegalArgumentException")
    void deleteRate_missing() {
        when(exchangeRateRepository.existsById(2L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.deleteRate(2L));
        verify(exchangeRateRepository, never()).deleteById(any());
    }

    /**
     * TC-FXSVC-16: tests {@code ExchangeRateService.deleteRate}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Only the requested id is deleted.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-16 | ExchangeRateService.deleteRate | only the requested id is deleted")
    void deleteRate_onlyRequested() {
        when(exchangeRateRepository.existsById(3L)).thenReturn(true);
        exchangeRateService.deleteRate(3L);
        verify(exchangeRateRepository).existsById(3L);
        verify(exchangeRateRepository, times(1)).deleteById(3L);
        verifyNoMoreInteractions(exchangeRateRepository);
    }

    /**
     * TC-FXSVC-17: tests {@code ExchangeRateService.getRate}.
     *
     * <p><b>Pre-condition:</b> Same currency PHP-&gt;PHP.</p>
     * <p><b>Post-condition:</b> 1, no DB lookup.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-17 | ExchangeRateService.getRate | same currency PHP->PHP -> 1, no DB lookup")
    void getRate_sameCurrency() {
        assertEquals(0, BigDecimal.ONE.compareTo(exchangeRateService.getRate("PHP", "php")));
        verifyNoInteractions(exchangeRateRepository);
    }

    /**
     * TC-FXSVC-18: tests {@code ExchangeRateService.getRate}.
     *
     * <p><b>Pre-condition:</b> Direct USD-&gt;PHP 58 stored.</p>
     * <p><b>Post-condition:</b> 58.000000.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-18 | ExchangeRateService.getRate | direct USD->PHP 58 stored -> 58.000000")
    void getRate_direct() {
        stubPair("USD", "PHP", "58");
        assertEquals(new BigDecimal("58.000000"), exchangeRateService.getRate("USD", "PHP"));
    }

    /**
     * TC-FXSVC-19: tests {@code ExchangeRateService.getRate}.
     *
     * <p><b>Pre-condition:</b> Only USD-&gt;PHP 50 stored, ask PHP-&gt;USD.</p>
     * <p><b>Post-condition:</b> Inverse 0.020000.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-19 | ExchangeRateService.getRate | only USD->PHP 50 stored, ask PHP->USD -> inverse 0.020000")
    void getRate_inverse() {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("PHP", "USD")).thenReturn(Optional.empty());
        stubPair("USD", "PHP", "50");
        assertEquals(new BigDecimal("0.020000"), exchangeRateService.getRate("PHP", "USD"));
    }

    /**
     * TC-FXSVC-20: tests {@code ExchangeRateService.getRate}.
     *
     * <p><b>Pre-condition:</b> USD-&gt;EUR via PHP (USD-&gt;PHP 60, EUR-&gt;PHP 75).</p>
     * <p><b>Post-condition:</b> 0.800000.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-20 | ExchangeRateService.getRate | USD->EUR via PHP (USD->PHP 60, EUR->PHP 75) -> 0.800000")
    void getRate_crossThroughPhp() {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(anyString(), anyString()))
                .thenReturn(Optional.empty());
        stubPair("USD", "PHP", "60");
        stubPair("EUR", "PHP", "75");
        assertEquals(new BigDecimal("0.800000"), exchangeRateService.getRate("USD", "EUR"));
    }

    /**
     * TC-FXSVC-21: tests {@code ExchangeRateService.getRate}.
     *
     * <p><b>Pre-condition:</b> No rate at all.</p>
     * <p><b>Post-condition:</b> IllegalArgumentException naming the pair.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-21 | ExchangeRateService.getRate | no rate at all -> IllegalArgumentException naming the pair")
    void getRate_missing() {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(anyString(), anyString()))
                .thenReturn(Optional.empty());
        Exception ex = assertThrows(IllegalArgumentException.class, () -> exchangeRateService.getRate("GBP", "SGD"));
        assertEquals("Exchange rate not found for GBP to SGD", ex.getMessage());
    }

    /**
     * TC-FXSVC-22: tests {@code ExchangeRateService.convert}.
     *
     * <p><b>Pre-condition:</b> 10 USD at 58.</p>
     * <p><b>Post-condition:</b> 580.00 PHP.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-22 | ExchangeRateService.convert | 10 USD at 58 -> 580.00 PHP")
    void convert_usdToPhp() {
        stubPair("USD", "PHP", "58");
        assertEquals(new BigDecimal("580.00"), exchangeRateService.convert(new BigDecimal("10"), "USD", "PHP"));
    }

    /**
     * TC-FXSVC-23: tests {@code ExchangeRateService.convert}.
     *
     * <p><b>Pre-condition:</b> 100 PHP to USD at 1/58.</p>
     * <p><b>Post-condition:</b> Rounded HALF_EVEN to 1.72.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-23 | ExchangeRateService.convert | 100 PHP to USD at 1/58 -> rounded HALF_EVEN to 1.72")
    void convert_rounding() {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("PHP", "USD")).thenReturn(Optional.empty());
        stubPair("USD", "PHP", "58");
        assertEquals(new BigDecimal("1.72"), exchangeRateService.convert(new BigDecimal("100"), "PHP", "USD"));
    }

    /**
     * TC-FXSVC-24: tests {@code ExchangeRateService.convert}.
     *
     * <p><b>Pre-condition:</b> Same currency.</p>
     * <p><b>Post-condition:</b> Amount unchanged at 2 dp.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-24 | ExchangeRateService.convert | same currency -> amount unchanged at 2 dp")
    void convert_sameCurrency() {
        assertEquals(new BigDecimal("12.50"), exchangeRateService.convert(new BigDecimal("12.5"), "EUR", "EUR"));
    }

    /**
     * TC-FXSVC-25: tests {@code ExchangeRateService.getQuote}.
     *
     * <p><b>Pre-condition:</b> 1000 PHP.</p>
     * <p><b>Post-condition:</b> EUR at 1/63 -&gt; 15.87 EUR quote.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-25 | ExchangeRateService.getQuote | 1000 PHP -> EUR at 1/63 -> 15.87 EUR quote")
    void getQuote_valid() {
        when(exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("PHP", "EUR")).thenReturn(Optional.empty());
        stubPair("EUR", "PHP", "63");

        ForexQuoteDTO quote = exchangeRateService.getQuote("php", "eur", new BigDecimal("1000"));

        assertEquals("PHP", quote.getFromCurrency());
        assertEquals("EUR", quote.getToCurrency());
        assertEquals(new BigDecimal("0.015873"), quote.getRate());
        assertEquals(new BigDecimal("15.87"), quote.getConvertedAmount());
    }

    /**
     * TC-FXSVC-26: tests {@code ExchangeRateService.getQuote}.
     *
     * <p><b>Pre-condition:</b> Amount 0.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-26 | ExchangeRateService.getQuote | amount 0 -> InvalidTransactionException")
    void getQuote_zeroAmount() {
        assertThrows(InvalidTransactionException.class,
                () -> exchangeRateService.getQuote("PHP", "USD", BigDecimal.ZERO));
    }

    /**
     * TC-FXSVC-27: tests {@code ExchangeRateService.getQuote}.
     *
     * <p><b>Pre-condition:</b> Unsupported currency XYZ.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-FXSVC-27 | ExchangeRateService.getQuote | unsupported currency XYZ -> InvalidTransactionException")
    void getQuote_unsupported() {
        assertThrows(InvalidTransactionException.class,
                () -> exchangeRateService.getQuote("XYZ", "USD", BigDecimal.TEN));
    }
}
