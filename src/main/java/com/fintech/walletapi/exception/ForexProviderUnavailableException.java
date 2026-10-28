package com.fintech.walletapi.exception;

/**
 * Thrown when a live-rate sync could not refresh a single rate because the
 * external provider was unreachable or answered with errors.
 *
 * <p>{@link GlobalExceptionHandler} turns it into a {@code 503 Service Unavailable}.
 * The stored rates are left untouched, so transfers keep working.</p>
 */
public class ForexProviderUnavailableException extends RuntimeException {

    /**
     * Creates the exception with a client-facing message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param message explanation returned to the client in the error body
     */
    public ForexProviderUnavailableException(String message) {
        super(message);
    }
}
