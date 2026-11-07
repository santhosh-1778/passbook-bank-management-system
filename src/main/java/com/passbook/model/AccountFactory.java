package com.passbook.model;

import com.passbook.exception.BankException;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FACTORY pattern: callers ask for a type by name and never call the constructors directly. */
public final class AccountFactory {
    private AccountFactory() {
    }

    /** A brand-new account with the bank's default terms. */
    public static Account createNew(String type, String accountNo, long customerId, BigDecimal opening,
                                    BigDecimal overdraftLimit) {
        return restore(type, accountNo, customerId, opening, AccountStatus.ACTIVE, LocalDate.now(),
                overdraftLimit, SavingsAccount.DEFAULT_MIN_BALANCE, SavingsAccount.DEFAULT_RATE);
    }

    /** Rebuilds an account read from storage. */
    public static Account restore(String type, String accountNo, long customerId, BigDecimal balance,
                                  AccountStatus status, LocalDate openDate, BigDecimal overdraftLimit,
                                  BigDecimal minBalance, double rate) {
        if ("savings".equals(type)) {
            return new SavingsAccount(accountNo, customerId, balance, status, openDate, minBalance, rate);
        }
        if ("current".equals(type)) {
            BigDecimal od = overdraftLimit == null ? Money.ZERO : overdraftLimit;
            if (od.signum() < 0) throw new BankException("Overdraft limit cannot be negative");
            return new CurrentAccount(accountNo, customerId, balance, status, openDate, od);
        }
        throw new BankException("Account type must be savings or current");
    }
}
