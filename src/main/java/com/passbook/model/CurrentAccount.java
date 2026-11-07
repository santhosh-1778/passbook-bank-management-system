package com.passbook.model;

import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/** INHERITANCE: a current account may go below zero up to its overdraft limit. */
public class CurrentAccount extends Account {
    private final BigDecimal overdraftLimit;

    public CurrentAccount(String accountNo, long customerId, BigDecimal balance, AccountStatus status,
                          LocalDate openDate, BigDecimal overdraftLimit) {
        super(accountNo, customerId, balance, status, openDate);
        this.overdraftLimit = Money.round(overdraftLimit);
    }

    public BigDecimal getOverdraftLimit() { return overdraftLimit; }

    @Override
    public boolean canWithdraw(BigDecimal amount) {
        return getBalance().add(overdraftLimit).compareTo(amount) >= 0;
    }

    @Override
    protected String ruleHint() {
        return "Overdraft limit is " + Money.format(overdraftLimit) + ".";
    }

    @Override
    public String type() { return "current"; }

    @Override
    public String termsSummary() {
        return "Overdraft up to " + Money.format(overdraftLimit);
    }

    @Override
    public Account copy() {
        return new CurrentAccount(getAccountNo(), getCustomerId(), getBalance(), getStatus(), getOpenDate(),
                overdraftLimit);
    }
}
