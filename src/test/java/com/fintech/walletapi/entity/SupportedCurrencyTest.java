package com.fintech.walletapi.entity;

import com.fintech.walletapi.exception.InvalidTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupportedCurrencyTest {

    @Test
    @DisplayName("TC-CUR-01 | SupportedCurrency.isSupported | 'USD' -> true")
    void isSupported_upper() {
        assertTrue(SupportedCurrency.isSupported("USD"));
    }

    @Test
    @DisplayName("TC-CUR-02 | SupportedCurrency.isSupported | ' php ' (lower case, spaces) -> true")
    void isSupported_lowerWithSpaces() {
        assertTrue(SupportedCurrency.isSupported(" php "));
    }

    @Test
    @DisplayName("TC-CUR-03 | SupportedCurrency.isSupported | 'JPY', blank and null -> false")
    void isSupported_unsupported() {
        assertFalse(SupportedCurrency.isSupported("JPY"));
        assertFalse(SupportedCurrency.isSupported(" "));
        assertFalse(SupportedCurrency.isSupported(null));
    }

    @Test
    @DisplayName("TC-CUR-04 | SupportedCurrency.fromCode | 'eur' -> EUR")
    void fromCode_valid() {
        assertEquals(SupportedCurrency.EUR, SupportedCurrency.fromCode("eur"));
    }

    @Test
    @DisplayName("TC-CUR-05 | SupportedCurrency.fromCode | 'BTC' -> InvalidTransactionException listing supported codes")
    void fromCode_invalid() {
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> SupportedCurrency.fromCode("BTC"));
        assertTrue(ex.getMessage().contains("PHP"));
    }

    @Test
    @DisplayName("TC-CUR-06 | SupportedCurrency.fromCode | null -> InvalidTransactionException")
    void fromCode_null() {
        assertThrows(InvalidTransactionException.class, () -> SupportedCurrency.fromCode(null));
    }

    @Test
    @DisplayName("TC-CUR-07 | SupportedCurrency.getDisplayName | PHP -> 'Philippine Peso'")
    void displayName_php() {
        assertEquals("Philippine Peso", SupportedCurrency.PHP.getDisplayName());
    }

    @Test
    @DisplayName("TC-CUR-08 | SupportedCurrency.getDisplayName | USD -> 'US Dollar'")
    void displayName_usd() {
        assertEquals("US Dollar", SupportedCurrency.USD.getDisplayName());
    }

    @Test
    @DisplayName("TC-CUR-09 | SupportedCurrency.getDisplayName | every constant has a non-blank name; HOME is PHP")
    void displayName_all() {
        for (SupportedCurrency c : SupportedCurrency.values()) {
            assertFalse(c.getDisplayName().isBlank(), c.name());
        }
        assertEquals(SupportedCurrency.PHP, SupportedCurrency.HOME);
    }
}
