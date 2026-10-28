package com.fintech.walletapi.exception;

import java.time.LocalDateTime;

/**
 * The JSON body returned for every error, so clients always get the same shape:
 * {@code {timestamp, status, errorType, message}}.
 */
public class ErrorResponseDTO {

    private LocalDateTime timestamp;
    private int status;
    private String errorType;
    private String message;

    /**
     * Creates an error body stamped with the current time.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param status    HTTP status code (e.g. 404)
     * @param errorType short category (e.g. "Not Found")
     * @param message   human-readable explanation
     */
    public ErrorResponseDTO(int status, String errorType, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.errorType = errorType;
        this.message = message;
    }

    /**
     * Returns when the error occurred.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets when the error occurred.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getTimestamp()} returns the value.</p>
     *
     * @param timestamp the timestamp
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the HTTP status code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Sets the HTTP status code.
     *
     * <p><b>Pre-condition:</b> a valid HTTP status. <b>Post-condition:</b> {@code getStatus()} returns the value.</p>
     *
     * @param status the status code
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Returns the error category.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the error type
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * Sets the error category.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getErrorType()} returns the value.</p>
     *
     * @param errorType the error type
     */
    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    /**
     * Returns the human-readable message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human-readable message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getMessage()} returns the value.</p>
     *
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
