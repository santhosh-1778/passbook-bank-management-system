package com.passbook.service;

import com.passbook.exception.AccessDeniedException;
import com.passbook.exception.BankException;
import com.passbook.model.Account;
import com.passbook.model.AccountStatus;
import com.passbook.model.CustomerProfile;
import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.model.Permission;
import com.passbook.model.Transaction;
import com.passbook.model.TransactionType;
import com.passbook.model.User;
import com.passbook.repo.AccountRepository;
import com.passbook.repo.CustomerRepository;
import com.passbook.repo.LoanRepository;
import com.passbook.repo.Repositories;
import com.passbook.repo.TransactionRepository;
import com.passbook.repo.UnitOfWork;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Loan application, approval and repayment (FR12-FR15). */
public class LoanService {
    private final LoanRepository loans;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final UnitOfWork uow;
    private final EventBus bus;

    public LoanService(Repositories r, EventBus bus) {
        this.loans = r.loans();
        this.accounts = r.accounts();
        this.customers = r.customers();
        this.transactions = r.transactions();
        this.uow = r.uow();
        this.bus = bus;
    }

    public record Application(long id, BigDecimal emi) {
    }

    public Application apply(User actor, String accountNo, BigDecimal amount, int tenureMonths, String purpose) {
        actor.require(Permission.SELF_SERVICE);
        long cid = actor.customerId();
        Loan loan = Loan.apply(cid, accountNo, amount, tenureMonths, purpose);  // validates & computes EMI (FR13)
        return uow.call(() -> {
            Account a = accounts.findByNo(accountNo).orElse(null);
            if (a == null || a.getCustomerId() != cid || a.getStatus() != AccountStatus.ACTIVE) {
                throw new BankException("Choose one of your active accounts to receive the loan");
            }
            loans.insert(loan);
            bus.publish(actor.getUserId(), "APPLY_LOAN", "loan " + loan.getId() + " " + Money.format(loan.getPrincipal()));
            return new Application(loan.getId(), loan.getEmi());
        });
    }

    public List<Loan> myLoans(User actor) {
        actor.require(Permission.SELF_SERVICE);
        return loans.findByCustomer(actor.customerId());
    }

    public List<LoanView> allLoans(User actor) {
        actor.require(Permission.APPROVE_LOAN);
        return toViews(loans.findAll());
    }

    public List<LoanView> pending(User actor) {
        actor.require(Permission.APPROVE_LOAN);
        List<Loan> out = new ArrayList<>();
        for (Loan l : loans.findAll()) if (l.getStatus() == LoanStatus.APPLIED) out.add(l);
        return toViews(out);
    }

    private List<LoanView> toViews(List<Loan> list) {
        List<LoanView> out = new ArrayList<>();
        for (Loan l : list) {
            out.add(new LoanView(l, customers.findById(l.getCustomerId()).map(CustomerProfile::name).orElse("?")));
        }
        return out;
    }

    /** FR14: manager decision. Approval disburses straight into the chosen account. */
    public void decide(User actor, long loanId, boolean approve) {
        actor.require(Permission.APPROVE_LOAN);
        uow.execute(() -> {
            Loan loan = loans.findById(loanId).orElseThrow(() -> new BankException("Loan not found"));
            if (approve) {
                Account a = accounts.findByNo(loan.getAccountNo()).orElseThrow(() -> new BankException("Account not found"));
                loan.approve(actor.getUserId());
                BigDecimal amt = a.deposit(loan.getPrincipal());
                accounts.update(a);
                transactions.insert(new Transaction(0, a.getAccountNo(), TransactionType.LOAN_DISBURSE, amt,
                        a.getBalance(), "Loan #" + loanId, LocalDateTime.now().withNano(0)));
            } else {
                loan.reject(actor.getUserId());
            }
            loans.update(loan);
            bus.publish(actor.getUserId(), approve ? "APPROVE_LOAN" : "REJECT_LOAN", "loan " + loanId);
        });
    }

    /** FR15: returns the amount still outstanding. */
    public BigDecimal repay(User actor, long loanId, String accountNo, BigDecimal amount) {
        actor.require(Permission.SELF_SERVICE);
        long cid = actor.customerId();
        return uow.call(() -> {
            Loan loan = loans.findById(loanId).filter(l -> l.getCustomerId() == cid)
                    .orElseThrow(() -> new BankException("This loan is not open for repayment"));
            Account a = accounts.findByNo(accountNo).orElseThrow(() -> new BankException("Account not found"));
            if (a.getCustomerId() != cid) throw new AccessDeniedException("Pay from one of your own accounts");
            BigDecimal pay = loan.repay(amount);
            a.withdraw(pay);
            accounts.update(a);
            transactions.insert(new Transaction(0, accountNo, TransactionType.LOAN_REPAY, pay, a.getBalance(),
                    "Loan #" + loanId, LocalDateTime.now().withNano(0)));
            loans.update(loan);
            bus.publish(actor.getUserId(), "REPAY_LOAN", "loan " + loanId + " " + Money.format(pay));
            return loan.getOutstanding();
        });
    }
}
