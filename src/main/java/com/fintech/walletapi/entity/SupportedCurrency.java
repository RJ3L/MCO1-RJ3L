package com.fintech.walletapi.entity;

import com.fintech.walletapi.exception.InvalidTransactionException;

import java.util.Arrays;
import java.util.Locale;

/**
 * The currencies a GMoney wallet can hold.
 *
 * <p>{@link #PHP} is the home currency: every new wallet defaults to it, and
 * the exchange-rate engine uses it as the bridge currency when no direct rate
 * exists between two foreign currencies.</p>
 */
public enum SupportedCurrency {
    PHP("Philippine Peso"),
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound"),
    SGD("Singapore Dollar");

    /** The currency every wallet uses when none is given. */
    public static final SupportedCurrency HOME = PHP;

    private final String displayName;

    /**
     * Creates a currency constant.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param displayName human-readable name shown to clients
     */
    SupportedCurrency(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable name of the currency.
     *
     * <p><b>Pre-condition:</b> none.</p>
     * <p><b>Post-condition:</b> the constant is unchanged.</p>
     *
     * @return the display name, e.g. {@code "US Dollar"}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks whether a currency code is supported by GMoney.
     *
     * <p><b>Pre-condition:</b> none ({@code null} and blank codes are allowed).</p>
     * <p><b>Post-condition:</b> no state is changed.</p>
     *
     * @param code an ISO 4217 code such as {@code "usd"} or {@code "PHP"}; case-insensitive
     * @return {@code true} if the code matches one of the constants, otherwise {@code false}
     */
    public static boolean isSupported(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(c -> c.name().equals(normalized));
    }

    /**
     * Converts a currency code into its constant.
     *
     * <p><b>Pre-condition:</b> {@code code} should be a supported ISO code.</p>
     * <p><b>Post-condition:</b> no state is changed.</p>
     *
     * @param code an ISO 4217 code, case-insensitive, surrounding spaces ignored
     * @return the matching {@link SupportedCurrency}
     * @throws InvalidTransactionException if the code is {@code null}, blank or not supported
     */
    public static SupportedCurrency fromCode(String code) {
        if (!isSupported(code)) {
            throw new InvalidTransactionException("Unsupported currency: " + code
                    + ". Supported currencies are " + Arrays.toString(values()));
        }
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
