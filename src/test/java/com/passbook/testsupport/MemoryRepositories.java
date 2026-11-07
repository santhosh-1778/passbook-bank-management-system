package com.passbook.testsupport;

import com.passbook.model.Account;
import com.passbook.model.AccountStatus;
import com.passbook.model.AuditEntry;
import com.passbook.model.CustomerProfile;
import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.model.Role;
import com.passbook.model.Transaction;
import com.passbook.model.User;
import com.passbook.repo.AccountRepository;
import com.passbook.repo.AuditRepository;
import com.passbook.repo.CustomerRepository;
import com.passbook.repo.LoanRepository;
import com.passbook.repo.Repositories;
import com.passbook.repo.TransactionRepository;
import com.passbook.repo.UnitOfWork;
import com.passbook.repo.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A complete in-memory implementation of every repository interface. It lets the business logic and the
 * web layer be tested without a database. Objects are copied on the way in and out, and a failed unit of
 * work restores a snapshot, so rollback behaves like a real database.
 */
public class MemoryRepositories {
    private final ReentrantLock lock = new ReentrantLock();
    private Map<Long, User> users = new LinkedHashMap<>();
    private Map<Long, CustomerProfile> customers = new LinkedHashMap<>();
    private Map<String, Account> accounts = new LinkedHashMap<>();
    private List<Transaction> transactions = new ArrayList<>();
    private Map<Long, Loan> loans = new LinkedHashMap<>();
    private List<AuditEntry> audit = new ArrayList<>();
    private long userSeq, customerSeq, txSeq, loanSeq, auditSeq, accountSeq;

    public Repositories create() {
        return new Repositories(new MemUow(), new MemUsers(), new MemCustomers(), new MemAccounts(),
                new MemTx(), new MemLoans(), new MemAudit());
    }

    // ---------------------------------------------------------------- unit of work with rollback
    private class MemUow implements UnitOfWork {
        @Override
        public <T> T call(Supplier<T> work) {
            lock.lock();
            Map<Long, User> u = copyUsers();
            Map<Long, CustomerProfile> c = new LinkedHashMap<>(customers);
            Map<String, Account> a = copyAccounts();
            List<Transaction> t = new ArrayList<>(transactions);
            Map<Long, Loan> l = copyLoans();
            List<AuditEntry> au = new ArrayList<>(audit);
            try {
                return work.get();
            } catch (RuntimeException | Error e) {
                users = u; customers = c; accounts = a; transactions = t; loans = l; audit = au;
                throw e;
            } finally {
                lock.unlock();
            }
        }
    }

    private Map<Long, User> copyUsers() {
        Map<Long, User> m = new LinkedHashMap<>();
        users.forEach((k, v) -> m.put(k, v.copy()));
        return m;
    }

    private Map<String, Account> copyAccounts() {
        Map<String, Account> m = new LinkedHashMap<>();
        accounts.forEach((k, v) -> m.put(k, v.copy()));
        return m;
    }

    private Map<Long, Loan> copyLoans() {
        Map<Long, Loan> m = new LinkedHashMap<>();
        loans.forEach((k, v) -> m.put(k, v.copy()));
        return m;
    }

    private class MemUsers implements UserRepository {
        public Optional<User> findById(long id) {
            User u = users.get(id);
            return Optional.ofNullable(u == null ? null : u.copy());
        }
        public Optional<User> findByUserId(String userId) {
            return users.values().stream().filter(u -> u.getUserId().equals(userId)).findFirst().map(User::copy);
        }
        public Optional<User> findByCustomerId(long cid) {
            return users.values().stream().filter(u -> u.customerId() != null && u.customerId() == cid)
                    .findFirst().map(User::copy);
        }
        public void insert(User user) {
            user.assignId(++userSeq);
            users.put(user.getId(), user.copy());
        }
        public void update(User user) { users.put(user.getId(), user.copy()); }
        public List<User> findEmployees() {
            return users.values().stream().filter(u -> u.role() != Role.CUSTOMER)
                    .sorted(Comparator.comparing((User u) -> u.role()).reversed().thenComparing(User::getUserId))
                    .map(User::copy).collect(Collectors.toList());
        }
        public List<User> findLocked() {
            return users.values().stream().filter(User::isLocked).map(User::copy).collect(Collectors.toList());
        }
        public boolean isEmpty() { return users.isEmpty(); }
    }

    private class MemCustomers implements CustomerRepository {
        public CustomerProfile insert(CustomerProfile p) {
            CustomerProfile saved = p.withId(++customerSeq);
            customers.put(saved.id(), saved);
            return saved;
        }
        public void update(CustomerProfile p) { customers.put(p.id(), p); }
        public Optional<CustomerProfile> findById(long id) { return Optional.ofNullable(customers.get(id)); }
        public List<CustomerProfile> search(String q) {
            String s = q.toLowerCase();
            return customers.values().stream()
                    .filter(c -> s.isEmpty() || c.name().toLowerCase().contains(s) || c.phone().contains(s)
                            || String.valueOf(c.id()).equals(s))
                    .sorted(Comparator.comparing(CustomerProfile::name)).collect(Collectors.toList());
        }
        public int count() { return customers.size(); }
    }

    private class MemAccounts implements AccountRepository {
        public String nextAccountNumber() { return String.valueOf(5000100000L + accountSeq + 1); }
        public void insert(Account a) {
            accountSeq++;
            accounts.put(a.getAccountNo(), a.copy());
        }
        public void update(Account a) { accounts.put(a.getAccountNo(), a.copy()); }
        public Optional<Account> findByNo(String no) {
            Account a = accounts.get(no);
            return Optional.ofNullable(a == null ? null : a.copy());
        }
        public List<Account> findByCustomer(long cid) {
            return accounts.values().stream().filter(a -> a.getCustomerId() == cid).map(Account::copy)
                    .collect(Collectors.toList());
        }
        public List<Account> findAllOpen() {
            return accounts.values().stream().filter(a -> a.getStatus() != AccountStatus.CLOSED)
                    .map(Account::copy).collect(Collectors.toList());
        }
    }

    private class MemTx implements TransactionRepository {
        public void insert(Transaction t) {
            transactions.add(new Transaction(++txSeq, t.accountNo(), t.type(), t.amount(), t.balanceAfter(),
                    t.note(), t.createdAt()));
        }
        public List<Transaction> latest(String no, int n) {
            List<Transaction> mine = transactions.stream().filter(t -> t.accountNo().equals(no))
                    .sorted(Comparator.comparingLong(Transaction::id).reversed()).collect(Collectors.toList());
            return mine.subList(0, Math.min(n, mine.size()));
        }
        public List<Transaction> between(String no, LocalDate from, LocalDate to) {
            return transactions.stream().filter(t -> t.accountNo().equals(no)
                    && !t.createdAt().toLocalDate().isBefore(from) && !t.createdAt().toLocalDate().isAfter(to))
                    .collect(Collectors.toList());
        }
        public List<Transaction> recent(int n) {
            List<Transaction> all = transactions.stream()
                    .sorted(Comparator.comparingLong(Transaction::id).reversed()).collect(Collectors.toList());
            return all.subList(0, Math.min(n, all.size()));
        }
    }

    private class MemLoans implements LoanRepository {
        public void insert(Loan l) {
            l.assignId(++loanSeq);
            loans.put(l.getId(), l.copy());
        }
        public void update(Loan l) { loans.put(l.getId(), l.copy()); }
        public Optional<Loan> findById(long id) {
            Loan l = loans.get(id);
            return Optional.ofNullable(l == null ? null : l.copy());
        }
        public List<Loan> findByCustomer(long cid) {
            return loans.values().stream().filter(l -> l.getCustomerId() == cid)
                    .sorted(Comparator.comparingLong(Loan::getId).reversed()).map(Loan::copy)
                    .collect(Collectors.toList());
        }
        public List<Loan> findAll() {
            return loans.values().stream().sorted(Comparator.comparingLong(Loan::getId).reversed())
                    .map(Loan::copy).collect(Collectors.toList());
        }
        public boolean hasActiveLoanOn(String no) {
            return loans.values().stream().anyMatch(l -> l.getAccountNo().equals(no) && l.isActive());
        }
    }

    private class MemAudit implements AuditRepository {
        public void append(AuditEntry e) {
            audit.add(new AuditEntry(++auditSeq, e.actor(), e.action(), e.detail(), e.createdAt()));
        }
        public List<AuditEntry> latest(int n) {
            List<AuditEntry> all = audit.stream().sorted(Comparator.comparingLong(AuditEntry::id).reversed())
                    .collect(Collectors.toList());
            return all.subList(0, Math.min(n, all.size()));
        }
    }
}
