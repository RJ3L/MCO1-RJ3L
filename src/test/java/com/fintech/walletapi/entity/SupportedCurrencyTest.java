package com.fintech.walletapi.entity;

import com.fintech.walletapi.exception.InvalidTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SupportedCurrency}.
 */
class SupportedCurrencyTest {

    /**
     * TC-CUR-01: tests {@code SupportedCurrency.isSupported}.
     *
     * <p><b>Pre-condition:</b> 'USD'.</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-CUR-01 | SupportedCurrency.isSupported | 'USD' -> true")
    void isSupported_upper() {
        assertTrue(SupportedCurrency.isSupported("USD"));
    }

    /**
     * TC-CUR-02: tests {@code SupportedCurrency.isSupported}.
     *
     * <p><b>Pre-condition:</b> ' php ' (lower case, spaces).</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-CUR-02 | SupportedCurrency.isSupported | ' php ' (lower case, spaces) -> true")
    void isSupported_lowerWithSpaces() {
        assertTrue(SupportedCurrency.isSupported(" php "));
    }

    /**
     * TC-CUR-03: tests {@code SupportedCurrency.isSupported}.
     *
     * <p><b>Pre-condition:</b> 'JPY', blank and null.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-CUR-03 | SupportedCurrency.isSupported | 'JPY', blank and null -> false")
    void isSupported_unsupported() {
        assertFalse(SupportedCurrency.isSupported("JPY"));
        assertFalse(SupportedCurrency.isSupported(" "));
        assertFalse(SupportedCurrency.isSupported(null));
    }

    /**
     * TC-CUR-04: tests {@code SupportedCurrency.fromCode}.
     *
     * <p><b>Pre-condition:</b> 'eur'.</p>
     * <p><b>Post-condition:</b> EUR.</p>
     */
    @Test
    @DisplayName("TC-CUR-04 | SupportedCurrency.fromCode | 'eur' -> EUR")
    void fromCode_valid() {
        assertEquals(SupportedCurrency.EUR, SupportedCurrency.fromCode("eur"));
    }

    /**
     * TC-CUR-05: tests {@code SupportedCurrency.fromCode}.
     *
     * <p><b>Pre-condition:</b> 'BTC'.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException listing supported codes.</p>
     */
    @Test
    @DisplayName("TC-CUR-05 | SupportedCurrency.fromCode | 'BTC' -> InvalidTransactionException listing supported codes")
    void fromCode_invalid() {
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> SupportedCurrency.fromCode("BTC"));
        assertTrue(ex.getMessage().contains("PHP"));
    }

    /**
     * TC-CUR-06: tests {@code SupportedCurrency.fromCode}.
     *
     * <p><b>Pre-condition:</b> Null.</p>
     * <p><b>Post-condition:</b> InvalidTransactionException.</p>
     */
    @Test
    @DisplayName("TC-CUR-06 | SupportedCurrency.fromCode | null -> InvalidTransactionException")
    void fromCode_null() {
        assertThrows(InvalidTransactionException.class, () -> SupportedCurrency.fromCode(null));
    }

    /**
     * TC-CUR-07: tests {@code SupportedCurrency.getDisplayName}.
     *
     * <p><b>Pre-condition:</b> PHP.</p>
     * <p><b>Post-condition:</b> 'Philippine Peso'.</p>
     */
    @Test
    @DisplayName("TC-CUR-07 | SupportedCurrency.getDisplayName | PHP -> 'Philippine Peso'")
    void displayName_php() {
        assertEquals("Philippine Peso", SupportedCurrency.PHP.getDisplayName());
    }

    /**
     * TC-CUR-08: tests {@code SupportedCurrency.getDisplayName}.
     *
     * <p><b>Pre-condition:</b> USD.</p>
     * <p><b>Post-condition:</b> 'US Dollar'.</p>
     */
    @Test
    @DisplayName("TC-CUR-08 | SupportedCurrency.getDisplayName | USD -> 'US Dollar'")
    void displayName_usd() {
        assertEquals("US Dollar", SupportedCurrency.USD.getDisplayName());
    }

    /**
     * TC-CUR-09: tests {@code SupportedCurrency.getDisplayName}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Every constant has a non-blank name; HOME is PHP.</p>
     */
    @Test
    @DisplayName("TC-CUR-09 | SupportedCurrency.getDisplayName | every constant has a non-blank name; HOME is PHP")
    void displayName_all() {
        for (SupportedCurrency c : SupportedCurrency.values()) {
            assertFalse(c.getDisplayName().isBlank(), c.name());
        }
        assertEquals(SupportedCurrency.PHP, SupportedCurrency.HOME);
    }
}
