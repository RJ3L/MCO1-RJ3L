package com.fintech.walletapi.entity;

import com.fintech.walletapi.exception.InvalidTransactionException;

import java.util.Arrays;
import java.util.Locale;

public enum SupportedCurrency {
    PHP("Philippine Peso"),
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound"),
    SGD("Singapore Dollar");

    public static final SupportedCurrency HOME = PHP;

    private final String displayName;

    SupportedCurrency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static boolean isSupported(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(c -> c.name().equals(normalized));
    }

    public static SupportedCurrency fromCode(String code) {
        if (!isSupported(code)) {
            throw new InvalidTransactionException("Unsupported currency: " + code
                    + ". Supported currencies are " + Arrays.toString(values()));
        }
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
