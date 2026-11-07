package com.passbook.exception;

/**
 * Root of the exception hierarchy. Every business-rule violation the user should see
 * (not a programming bug) is a BankException, so the web layer can catch just this type.
 */
public class BankException extends RuntimeException {
    public BankException(String message) {
        super(message);
    }
}
