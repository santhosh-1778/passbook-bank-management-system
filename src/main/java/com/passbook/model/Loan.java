package com.passbook.model;

import com.passbook.exception.BankException;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A loan owns its life-cycle rules: Applied -> Disbursed -> Closed, or Applied -> Rejected. */
public class Loan {
    public static final double DEFAULT_RATE = 10.0;

    private long id;
    private final long customerId;
    private final String accountNo;
    private final BigDecimal principal;
    private final double rate;
    private final int tenureMonths;
    private final String purpose;
    private final BigDecimal emi;
    private BigDecimal outstanding;
    private LoanStatus status;
    private final LocalDateTime appliedAt;
    private String decidedBy;
    private LocalDateTime decidedAt;

    public Loan(long id, long customerId, String accountNo, BigDecimal principal, double rate, int tenureMonths,
                String purpose, BigDecimal emi, BigDecimal outstanding, LoanStatus status,
                LocalDateTime appliedAt, String decidedBy, LocalDateTime decidedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountNo = accountNo;
        this.principal = principal;
        this.rate = rate;
        this.tenureMonths = tenureMonths;
        this.purpose = purpose;
        this.emi = emi;
        this.outstanding = outstanding;
        this.status = status;
        this.appliedAt = appliedAt;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    /** Validating factory for a new application. */
    public static Loan apply(long customerId, String accountNo, BigDecimal amount, int tenureMonths, String purpose) {
        BigDecimal p = Money.validatedAmount(amount);
        if (p.compareTo(new BigDecimal("1000")) < 0 || p.compareTo(new BigDecimal("5000000")) > 0) {
            throw new BankException("Loan amount must be between 1,000 and 5,000,000");
        }
        if (tenureMonths < 3 || tenureMonths > 360) {
            throw new BankException("Tenure must be between 3 and 360 months");
        }
        String why = purpose == null ? "" : purpose.trim();
        if (why.isEmpty()) throw new BankException("Purpose is required");
        if (why.length() > 120) throw new BankException("Purpose is too long");
        return new Loan(0, customerId, accountNo, p, DEFAULT_RATE, tenureMonths, why,
                calculateEmi(p, DEFAULT_RATE, tenureMonths), Money.ZERO, LoanStatus.APPLIED,
                LocalDateTime.now().withNano(0), null, null);
    }

    /** EMI = P r (1+r)^n / ((1+r)^n - 1), r = monthly rate. */
    public static BigDecimal calculateEmi(BigDecimal principal, double annualRatePercent, int months) {
        double r = annualRatePercent / 12 / 100;
        if (r == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
        }
        double f = Math.pow(1 + r, months);
        return Money.ofDouble(principal.doubleValue() * r * f / (f - 1));
    }

    public void approve(String by) {
        requireApplied();
        status = LoanStatus.DISBURSED;
        outstanding = Money.round(emi.multiply(BigDecimal.valueOf(tenureMonths)));
        decidedBy = by;
        decidedAt = LocalDateTime.now().withNano(0);
    }

    public void reject(String by) {
        requireApplied();
        status = LoanStatus.REJECTED;
        decidedBy = by;
        decidedAt = LocalDateTime.now().withNano(0);
    }

    /** Applies a payment (at most the outstanding amount) and returns how much was actually taken. */
    public BigDecimal repay(BigDecimal amount) {
        if (status != LoanStatus.DISBURSED) {
            throw new BankException("This loan is not open for repayment");
        }
        BigDecimal pay = Money.validatedAmount(amount).min(outstanding);
        outstanding = outstanding.subtract(pay);
        if (outstanding.signum() == 0) status = LoanStatus.CLOSED;
        return pay;
    }

    private void requireApplied() {
        if (status != LoanStatus.APPLIED) {
            throw new BankException("Only applications in 'Applied' state can be decided");
        }
    }

    public boolean isActive() {
        return status == LoanStatus.APPLIED || status == LoanStatus.DISBURSED;
    }

    public void assignId(long newId) {
        if (id != 0) throw new IllegalStateException("id already assigned");
        id = newId;
    }

    public long getId() { return id; }
    public long getCustomerId() { return customerId; }
    public String getAccountNo() { return accountNo; }
    public BigDecimal getPrincipal() { return principal; }
    public double getRate() { return rate; }
    public int getTenureMonths() { return tenureMonths; }
    public String getPurpose() { return purpose; }
    public BigDecimal getEmi() { return emi; }
    public BigDecimal getOutstanding() { return outstanding; }
    public LoanStatus getStatus() { return status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public String getDecidedBy() { return decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }

    public Loan copy() {
        return new Loan(id, customerId, accountNo, principal, rate, tenureMonths, purpose, emi, outstanding,
                status, appliedAt, decidedBy, decidedAt);
    }
}
