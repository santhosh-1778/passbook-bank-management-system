package com.passbook.model;

import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/** INHERITANCE: a savings account is an Account that must keep a minimum balance and earns interest. */
public class SavingsAccount extends Account implements InterestBearing {
    public static final BigDecimal DEFAULT_MIN_BALANCE = new BigDecimal("1000.00");
    public static final double DEFAULT_RATE = 4.0;

    private final BigDecimal minBalance;
    private final double interestRate;

    public SavingsAccount(String accountNo, long customerId, BigDecimal balance, AccountStatus status,
                          LocalDate openDate, BigDecimal minBalance, double interestRate) {
        super(accountNo, customerId, balance, status, openDate);
        this.minBalance = Money.round(minBalance);
        this.interestRate = interestRate;
    }

    public BigDecimal getMinBalance() { return minBalance; }

    @Override
    public boolean canWithdraw(BigDecimal amount) {
        return getBalance().subtract(amount).compareTo(minBalance) >= 0;
    }

    @Override
    protected String ruleHint() {
        return "Savings accounts must keep a minimum balance of " + Money.format(minBalance) + ".";
    }

    @Override
    public String type() { return "savings"; }

    @Override
    public String termsSummary() {
        return interestRate + "% p.a. · min " + Money.format(minBalance);
    }

    @Override
    public BigDecimal monthlyInterest() {
        return getBalance().multiply(BigDecimal.valueOf(interestRate))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public double annualRatePercent() { return interestRate; }

    @Override
    public Account copy() {
        return new SavingsAccount(getAccountNo(), getCustomerId(), getBalance(), getStatus(), getOpenDate(),
                minBalance, interestRate);
    }
}
