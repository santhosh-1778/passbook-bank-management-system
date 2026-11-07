package com.passbook.exception;

import com.passbook.util.Money;

import java.math.BigDecimal;

public class InsufficientFundsException extends BankException {
    public InsufficientFundsException(String accountNo, BigDecimal amount, String ruleHint) {
        super("Insufficient funds in " + accountNo + " for amount " + Money.format(amount) + ". " + ruleHint);
    }
}
