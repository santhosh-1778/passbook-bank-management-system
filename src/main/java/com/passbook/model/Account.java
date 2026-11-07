package com.passbook.model;

import com.passbook.exception.BankException;
import com.passbook.exception.InsufficientFundsException;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ABSTRACTION + ENCAPSULATION: the balance is private and can only change through
 * deposit() and withdraw(), which enforce the rules. Subclasses supply only the part
 * that differs (canWithdraw), which makes withdraw() a Template Method.
 */
public abstract class Account {
    private final String accountNo;
    private final long customerId;
    private final LocalDate openDate;
    private BigDecimal balance;
    private AccountStatus status;

    protected Account(String accountNo, long customerId, BigDecimal balance, AccountStatus status, LocalDate openDate) {
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.balance = Money.round(balance);
        this.status = status;
        this.openDate = openDate;
    }

    public String getAccountNo() { return accountNo; }
    public long getCustomerId() { return customerId; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public LocalDate getOpenDate() { return openDate; }

    public final BigDecimal deposit(BigDecimal amount) {
        BigDecimal a = Money.validatedAmount(amount);
        requireActive();
        balance = balance.add(a);
        return a;
    }

    public final BigDecimal withdraw(BigDecimal amount) {
        BigDecimal a = Money.validatedAmount(amount);
        requireActive();
        if (!canWithdraw(a)) {
            throw new InsufficientFundsException(accountNo, a, ruleHint());
        }
        balance = balance.subtract(a);
        return a;
    }

    public final void setFrozen(boolean frozen) {
        if (status == AccountStatus.CLOSED) {
            throw new BankException("Closed accounts cannot be changed");
        }
        status = frozen ? AccountStatus.FROZEN : AccountStatus.ACTIVE;
    }

    public final void close() {
        if (status == AccountStatus.CLOSED) {
            throw new BankException("Account is already closed");
        }
        if (balance.signum() != 0) {
            throw new BankException("Cannot close: balance must be zero (currently " + Money.format(balance) + ")");
        }
        status = AccountStatus.CLOSED;
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new BankException("Account " + accountNo + " is " + status.label().toLowerCase()
                    + " and cannot be used for transactions");
        }
    }

    // ---- POLYMORPHISM: each subclass answers these differently ----
    public abstract boolean canWithdraw(BigDecimal amount);

    protected abstract String ruleHint();

    /** "savings" or "current". */
    public abstract String type();

    /** Short text shown on the passbook cover. */
    public abstract String termsSummary();

    /** Prototype pattern: an independent copy (used by the in-memory test store). */
    public abstract Account copy();

    @Override
    public String toString() {
        return type() + " " + accountNo + " [" + status.label() + "] " + Money.format(balance);
    }
}
