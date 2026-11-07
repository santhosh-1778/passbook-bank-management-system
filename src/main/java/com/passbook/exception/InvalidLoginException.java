package com.passbook.exception;

public class InvalidLoginException extends BankException {
    public InvalidLoginException(String message) {
        super(message);
    }
}
