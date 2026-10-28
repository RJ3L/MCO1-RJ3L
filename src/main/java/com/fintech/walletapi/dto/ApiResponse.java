package com.fintech.walletapi.dto;

import java.time.LocalDateTime;

/**
 * Standard envelope for every successful response:
 * {@code {timestamp, status, message, data}}.
 *
 * @param <T> type of the payload in {@code data}
 */
public class ApiResponse<T> {

    private LocalDateTime timestamp;
    private int status;
    private String message;
    private T data;

    /**
     * Creates an empty response stamped with the current time (used by JSON deserialization).
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Creates a response that carries a payload.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param status  HTTP status code
     * @param message human-readable message
     * @param data    payload
     */
    public ApiResponse(int status, String message, T data) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a response without a payload.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param status  HTTP status code
     * @param message human-readable message
     */
    public ApiResponse(int status, String message) {
        this(status, message, null);
    }

    /**
     * Returns when the response was created.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets when the response was created.
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
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getStatus()} returns the value.</p>
     *
     * @param status the status code
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Returns the message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getMessage()} returns the value.</p>
     *
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the payload.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the payload, or {@code null}
     */
    public T getData() {
        return data;
    }

    /**
     * Sets the payload.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getData()} returns the value.</p>
     *
     * @param data the payload
     */
    public void setData(T data) {
        this.data = data;
    }
}
