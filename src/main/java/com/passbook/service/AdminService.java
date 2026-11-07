package com.passbook.service;

import com.passbook.exception.BankException;
import com.passbook.model.Account;
import com.passbook.model.AuditEntry;
import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.model.Permission;
import com.passbook.model.Role;
import com.passbook.model.Transaction;
import com.passbook.model.User;
import com.passbook.model.UserFactory;
import com.passbook.repo.AccountRepository;
import com.passbook.repo.AuditRepository;
import com.passbook.repo.CustomerRepository;
import com.passbook.repo.LoanRepository;
import com.passbook.repo.Repositories;
import com.passbook.repo.TransactionRepository;
import com.passbook.repo.UnitOfWork;
import com.passbook.repo.UserRepository;
import com.passbook.security.PasswordHasher;
import com.passbook.util.Money;

import java.math.BigDecimal;
import java.util.List;

/** Staff accounts, unlocking, audit trail and branch statistics (FR18, FR19). */
public class AdminService {
    private final UserRepository users;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final LoanRepository loans;
    private final TransactionRepository transactions;
    private final AuditRepository audit;
    private final UnitOfWork uow;
    private final PasswordHasher hasher;
    private final EventBus bus;

    public AdminService(Repositories r, PasswordHasher hasher, EventBus bus) {
        this.users = r.users();
        this.customers = r.customers();
        this.accounts = r.accounts();
        this.loans = r.loans();
        this.transactions = r.transactions();
        this.audit = r.audit();
        this.uow = r.uow();
        this.hasher = hasher;
        this.bus = bus;
    }

    public void createEmployee(User actor, String userId, String fullName, String password, Role role) {
        actor.require(Permission.MANAGE_STAFF);
        String id = userId == null ? "" : userId.trim();
        String name = fullName == null ? "" : fullName.trim();
        if (id.isEmpty() || id.length() > 40) throw new BankException("User ID is required (up to 40 characters)");
        if (name.isEmpty() || name.length() > 80) throw new BankException("Full name is required");
        if (role == null || role == Role.CUSTOMER) throw new BankException("Role must be teller or manager");
        if (password == null || password.length() < 6) throw new BankException("Password must be at least 6 characters");
        uow.execute(() -> {
            if (users.findByUserId(id).isPresent()) throw new BankException("That user ID is already taken");
            users.insert(UserFactory.newEmployee(role, id, name, hasher.hash(password)));
            bus.publish(actor.getUserId(), "CREATE_EMPLOYEE", id + " (" + role.label() + ")");
        });
    }

    public void setActive(User actor, long userId, boolean active) {
        actor.require(Permission.MANAGE_STAFF);
        uow.execute(() -> {
            User u = users.findById(userId).filter(x -> x.role() != Role.CUSTOMER)
                    .orElseThrow(() -> new BankException("Employee not found"));
            if (u.getUserId().equals(actor.getUserId()) && !active) {
                throw new BankException("You cannot disable your own user");
            }
            u.setActive(active);
            users.update(u);
            bus.publish(actor.getUserId(), active ? "ENABLE_USER" : "DISABLE_USER", u.getUserId());
        });
    }

    public void unlock(User actor, long userId) {
        actor.require(Permission.MANAGE_STAFF);
        uow.execute(() -> {
            User u = users.findById(userId).orElseThrow(() -> new BankException("User not found"));
            u.unlock();
            users.update(u);
            bus.publish(actor.getUserId(), "UNLOCK_USER", u.getUserId());
        });
    }

    public List<User> employees(User actor) {
        actor.require(Permission.MANAGE_STAFF);
        return users.findEmployees();
    }

    public List<User> lockedUsers(User actor) {
        actor.require(Permission.MANAGE_STAFF);
        return users.findLocked();
    }

    public List<AuditEntry> auditTrail(User actor) {
        actor.require(Permission.VIEW_AUDIT);
        return audit.latest(200);
    }

    public Stats stats(User actor) {
        actor.require(Permission.MANAGE_CUSTOMERS);
        BigDecimal deposits = Money.ZERO;
        List<Account> open = accounts.findAllOpen();
        for (Account a : open) deposits = deposits.add(a.getBalance());
        int pending = 0;
        BigDecimal book = Money.ZERO;
        for (Loan l : loans.findAll()) {
            if (l.getStatus() == LoanStatus.APPLIED) pending++;
            if (l.getStatus() == LoanStatus.DISBURSED) book = book.add(l.getOutstanding());
        }
        return new Stats(customers.count(), open.size(), deposits, pending, book);
    }

    public List<Transaction> recentTransactions(User actor) {
        actor.require(Permission.MANAGE_CUSTOMERS);
        return transactions.recent(8);
    }
}
