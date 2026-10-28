package com.fintech.walletapi.exception;

public class DuplicateWalletException extends RuntimeException {

    public DuplicateWalletException(String message) {
        super(message);
    }
}
