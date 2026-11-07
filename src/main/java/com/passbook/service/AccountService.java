package com.passbook.service;

import com.passbook.exception.AccessDeniedException;
import com.passbook.exception.AccountNotFoundException;
import com.passbook.exception.BankException;
import com.passbook.model.Account;
import com.passbook.model.AccountFactory;
import com.passbook.model.AccountStatus;
import com.passbook.model.Customer;
import com.passbook.model.CustomerProfile;
import com.passbook.model.InterestBearing;
import com.passbook.model.Permission;
import com.passbook.model.SavingsAccount;
import com.passbook.model.Transaction;
import com.passbook.model.TransactionType;
import com.passbook.model.User;
import com.passbook.repo.AccountRepository;
import com.passbook.repo.CustomerRepository;
import com.passbook.repo.LoanRepository;
import com.passbook.repo.Repositories;
import com.passbook.repo.TransactionRepository;
import com.passbook.repo.UnitOfWork;
import com.passbook.repo.UserRepository;
import com.passbook.security.PasswordHasher;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Accounts, customers, money movement and statements (FR1-FR11, FR16). */
public class AccountService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final LoanRepository loans;
    private final UnitOfWork uow;
    private final PasswordHasher hasher;
    private final EventBus bus;
    private final SecureRandom random = new SecureRandom();

    public AccountService(Repositories r, PasswordHasher hasher, EventBus bus) {
        this.accounts = r.accounts();
        this.customers = r.customers();
        this.transactions = r.transactions();
        this.users = r.users();
        this.loans = r.loans();
        this.uow = r.uow();
        this.hasher = hasher;
        this.bus = bus;
    }

    // ------------------------------------------------------------------ opening (FR1, FR2)
    public OpenedAccount openAccount(User actor, OpenAccountRequest req) {
        actor.require(Permission.OPEN_ACCOUNT);
        BigDecimal opening = req.opening() == null ? Money.ZERO : Money.round(req.opening());
        if (opening.signum() < 0) throw new BankException("Opening deposit cannot be negative");
        if (!"savings".equals(req.type()) && !"current".equals(req.type())) {
            throw new BankException("Choose savings or current");
        }
        if ("savings".equals(req.type()) && opening.compareTo(SavingsAccount.DEFAULT_MIN_BALANCE) < 0) {
            throw new BankException("Savings accounts need an opening deposit of at least "
                    + Money.format(SavingsAccount.DEFAULT_MIN_BALANCE));
        }
        BigDecimal overdraft = req.overdraftLimit() == null ? Money.ZERO : Money.round(req.overdraftLimit());
        if (overdraft.signum() < 0) throw new BankException("Overdraft limit cannot be negative");

        return uow.call(() -> {
            long customerId;
            String loginId = null;
            String tempPassword = null;
            if (req.existingCustomerId() != null) {
                customerId = req.existingCustomerId();
                customers.findById(customerId).orElseThrow(() -> new BankException("Customer not found"));
            } else {
                CustomerDetails d = req.newCustomer();
                if (d == null) throw new BankException("Customer details are required");
                CustomerProfile saved = customers.insert(new CustomerProfile(0, d.name(), d.address(), d.phone(),
                        d.idProof(), LocalDateTime.now().withNano(0)));
                customerId = saved.id();
                loginId = String.format("cust%04d", customerId);
                tempPassword = randomPassword(10);
                users.insert(new Customer(0, loginId, saved.name(), hasher.hash(tempPassword), 0, false, true,
                        customerId));
            }
            String no = accounts.nextAccountNumber();
            Account acc = AccountFactory.createNew(req.type(), no, customerId, opening, overdraft);
            accounts.insert(acc);
            if (opening.signum() > 0) {
                post(acc, TransactionType.DEPOSIT, opening, "Opening deposit");
            }
            bus.publish(actor.getUserId(), "OPEN_ACCOUNT", no + " " + req.type() + " for customer " + customerId);
            return new OpenedAccount(no, customerId, loginId, tempPassword);
        });
    }

    private String randomPassword(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    // ------------------------------------------------------------------ reading
    public AccountDetails details(User actor, String accountNo) {
        Account a = loadAuthorized(actor, accountNo);
        String owner = customers.findById(a.getCustomerId()).map(CustomerProfile::name).orElse("?");
        return new AccountDetails(a, owner);
    }

    public List<Account> accountsOf(User actor, long customerId) {
        if (!actor.can(Permission.SERVE_ANY_ACCOUNT)
                && (actor.customerId() == null || actor.customerId() != customerId)) {
            throw new AccessDeniedException("You can only see your own accounts.");
        }
        return accounts.findByCustomer(customerId);
    }

    public List<Transaction> miniStatement(User actor, String accountNo) {
        loadAuthorized(actor, accountNo);
        return transactions.latest(accountNo, 10);  // FR10
    }

    public List<Transaction> statement(User actor, String accountNo, LocalDate from, LocalDate to) {
        loadAuthorized(actor, accountNo);
        if (from.isAfter(to)) throw new BankException("The start date must not be after the end date.");
        return transactions.between(accountNo, from, to);  // FR11
    }

    // ------------------------------------------------------------------ money movement (FR5-FR9)
    public BigDecimal deposit(User actor, String accountNo, BigDecimal amount) {
        return uow.call(() -> {
            Account a = loadAuthorized(actor, accountNo);
            BigDecimal amt = a.deposit(amount);
            post(a, TransactionType.DEPOSIT, amt, null);
            bus.publish(actor.getUserId(), "DEPOSIT", accountNo + " " + Money.format(amt));
            return a.getBalance();
        });
    }

    public BigDecimal withdraw(User actor, String accountNo, BigDecimal amount) {
        return uow.call(() -> {
            Account a = loadAuthorized(actor, accountNo);
            BigDecimal amt = a.withdraw(amount);  // polymorphic rule check inside
            post(a, TransactionType.WITHDRAW, amt, null);
            bus.publish(actor.getUserId(), "WITHDRAW", accountNo + " " + Money.format(amt));
            return a.getBalance();
        });
    }

    /** FR7: atomic. If anything throws, neither balance changes. Returns the source balance. */
    public BigDecimal transfer(User actor, String fromNo, String toNo, BigDecimal amount) {
        if (fromNo.equals(toNo)) throw new BankException("Source and destination must be different accounts");
        return uow.call(() -> {
            Account src = loadAuthorized(actor, fromNo);
            Account dst = accounts.findByNo(toNo).orElseThrow(() -> new AccountNotFoundException(toNo));
            BigDecimal amt = src.withdraw(amount);
            dst.deposit(amt);
            post(src, TransactionType.TRANSFER_OUT, amt, "To " + toNo);
            post(dst, TransactionType.TRANSFER_IN, amt, "From " + fromNo);
            bus.publish(actor.getUserId(), "TRANSFER", fromNo + " -> " + toNo + " " + Money.format(amt));
            return src.getBalance();
        });
    }

    /** FR16: credits one month of interest to every active account that bears interest. */
    public int creditMonthlyInterest(User actor) {
        actor.require(Permission.CREDIT_INTEREST);
        return uow.call(() -> {
            int n = 0;
            for (Account a : accounts.findAllOpen()) {
                if (a instanceof InterestBearing && a.getStatus() == AccountStatus.ACTIVE) {
                    BigDecimal interest = ((InterestBearing) a).monthlyInterest();
                    if (interest.signum() > 0) {
                        a.deposit(interest);
                        post(a, TransactionType.INTEREST, interest, "Monthly interest");
                        n++;
                    }
                }
            }
            bus.publish(actor.getUserId(), "CREDIT_INTEREST", n + " accounts");
            return n;
        });
    }

    // ------------------------------------------------------------------ account life-cycle (FR4)
    public void close(User actor, String accountNo) {
        actor.require(Permission.CLOSE_ACCOUNT);
        uow.execute(() -> {
            Account a = loadAuthorized(actor, accountNo);
            if (loans.hasActiveLoanOn(accountNo)) {
                throw new BankException("Cannot close: this account has an active or pending loan");
            }
            a.close();
            accounts.update(a);
            bus.publish(actor.getUserId(), "CLOSE_ACCOUNT", accountNo);
        });
    }

    public void setFrozen(User actor, String accountNo, boolean frozen) {
        actor.require(Permission.FREEZE_ACCOUNT);
        uow.execute(() -> {
            Account a = loadAuthorized(actor, accountNo);
            a.setFrozen(frozen);
            accounts.update(a);
            bus.publish(actor.getUserId(), frozen ? "FREEZE" : "UNFREEZE", accountNo);
        });
    }

    // ------------------------------------------------------------------ customers (FR3)
    public CustomerProfile customer(User actor, long id) {
        actor.require(Permission.MANAGE_CUSTOMERS);
        return customers.findById(id).orElseThrow(() -> new BankException("Customer not found"));
    }

    public CustomerProfile ownProfile(User actor) {
        if (actor.customerId() == null) throw new AccessDeniedException("Not a customer");
        return customers.findById(actor.customerId()).orElseThrow(() -> new BankException("Customer not found"));
    }

    public record CustomerRow(CustomerProfile profile, int openAccounts) {
    }

    public List<CustomerRow> searchCustomers(User actor, String q) {
        actor.require(Permission.MANAGE_CUSTOMERS);
        List<CustomerRow> rows = new ArrayList<>();
        for (CustomerProfile p : customers.search(q == null ? "" : q.trim())) {
            int open = 0;
            for (Account a : accounts.findByCustomer(p.id())) {
                if (a.getStatus() != AccountStatus.CLOSED) open++;
            }
            rows.add(new CustomerRow(p, open));
        }
        return rows;
    }

    public void updateProfile(User actor, long id, CustomerDetails d) {
        actor.require(Permission.MANAGE_CUSTOMERS);
        uow.execute(() -> {
            CustomerProfile old = customers.findById(id).orElseThrow(() -> new BankException("Customer not found"));
            CustomerProfile updated = new CustomerProfile(id, d.name(), d.address(), d.phone(), d.idProof(),
                    old.createdAt());
            customers.update(updated);
            users.findByCustomerId(id).ifPresent(u -> {
                u.rename(updated.name());
                users.update(u);
            });
            bus.publish(actor.getUserId(), "UPDATE_PROFILE", "customer " + id);
        });
    }

    // ------------------------------------------------------------------ helpers
    private Account loadAuthorized(User actor, String accountNo) {
        Account a = accounts.findByNo(accountNo).orElseThrow(() -> new AccountNotFoundException(accountNo));
        boolean staff = actor.can(Permission.SERVE_ANY_ACCOUNT);
        boolean owner = actor.customerId() != null && actor.customerId() == a.getCustomerId();
        if (!staff && !owner) throw new AccessDeniedException("This account is not yours.");
        return a;
    }

    /** FR8: every balance change is saved together with its ledger entry. */
    private void post(Account a, TransactionType type, BigDecimal amount, String note) {
        accounts.update(a);
        transactions.insert(new Transaction(0, a.getAccountNo(), type, amount, a.getBalance(), note,
                LocalDateTime.now().withNano(0)));
    }
}
