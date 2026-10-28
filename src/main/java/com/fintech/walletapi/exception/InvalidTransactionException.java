package com.fintech.walletapi.exception;

/**
 * Thrown when a request is well-formed but breaks a GMoney business rule
 * (unsupported currency, sending money to the same wallet, a duplicate
 * exchange rate, an amount with more than two decimal places, ...).
 *
 * <p>{@link GlobalExceptionHandler} turns it into a {@code 400 Bad Request}.</p>
 */
public class InvalidTransactionException extends RuntimeException {

    /**
     * Creates the exception with a client-facing message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param message explanation returned to the client in the error body
     */
    public InvalidTransactionException(String message) {
        super(message);
    }
}
