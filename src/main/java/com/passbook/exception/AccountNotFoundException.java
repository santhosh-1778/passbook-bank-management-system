package com.passbook.exception;

public class AccountNotFoundException extends BankException {
    public AccountNotFoundException(String accountNo) {
        super("Account not found: " + accountNo);
    }
}
