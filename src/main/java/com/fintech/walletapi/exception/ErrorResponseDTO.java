package com.fintech.walletapi.exception;

import java.time.LocalDateTime;

public class ErrorResponseDTO {

    private LocalDateTime timestamp;
    private int status;
    private String errorType;
    private String message;

    public ErrorResponseDTO(int status, String errorType, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.errorType = errorType;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
