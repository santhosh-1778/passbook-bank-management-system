package com.passbook.exception;

public class AccessDeniedException extends BankException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
