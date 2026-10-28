package com.fintech.walletapi.exception;

/**
 * Thrown when a customer tries to open a second wallet in a currency they
 * already hold (each customer may have at most one wallet per currency).
 *
 * <p>{@link GlobalExceptionHandler} turns it into a {@code 409 Conflict}.</p>
 */
public class DuplicateWalletException extends RuntimeException {

    /**
     * Creates the exception with a client-facing message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param message explanation returned to the client in the error body
     */
    public DuplicateWalletException(String message) {
        super(message);
    }
}
