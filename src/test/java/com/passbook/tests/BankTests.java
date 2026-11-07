package com.passbook.tests;

import com.passbook.exception.AccessDeniedException;
import com.passbook.exception.AccountNotFoundException;
import com.passbook.exception.BankException;
import com.passbook.exception.InsufficientFundsException;
import com.passbook.exception.InvalidLoginException;
import com.passbook.model.Account;
import com.passbook.model.AccountStatus;
import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.model.Permission;
import com.passbook.model.Role;
import com.passbook.model.TransactionType;
import com.passbook.model.User;
import com.passbook.repo.Repositories;
import com.passbook.security.PasswordHasher;
import com.passbook.security.Pbkdf2PasswordHasher;
import com.passbook.service.AccountService;
import com.passbook.service.AdminService;
import com.passbook.service.AuditListener;
import com.passbook.service.AuthService;
import com.passbook.service.CustomerDetails;
import com.passbook.service.DemoData;
import com.passbook.service.EventBus;
import com.passbook.service.LoanService;
import com.passbook.service.OpenAccountRequest;
import com.passbook.service.OpenedAccount;
import com.passbook.testsupport.MemoryRepositories;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.passbook.tests.T.check;
import static com.passbook.tests.T.eq;
import static com.passbook.tests.T.eqMoney;
import static com.passbook.tests.T.test;
import static com.passbook.tests.T.throwsA;

/** Automated versions of the report's test cases TC1-TC9, plus loans, interest, OOP and concurrency checks. */
public final class BankTests {
    private BankTests() {
    }

    /** Everything wired together exactly as in App, but on the in-memory store. */
    public static final class World {
        public final Repositories repos;
        public final AuthService auth;
        public final AccountService accounts;
        public final LoanService loans;
        public final AdminService admin;
        public final PasswordHasher hasher = new Pbkdf2PasswordHasher();
        public final User manager, teller;
        public final OpenedAccount first;
        public final String savings, current;
        public final long customerId;
        public final String login, tempPassword;

        public World() {
            repos = new MemoryRepositories().create();
            EventBus bus = new EventBus();
            bus.subscribe(new AuditListener(repos.audit()));
            auth = new AuthService(repos, hasher, bus);
            accounts = new AccountService(repos, hasher, bus);
            loans = new LoanService(repos, bus);
            admin = new AdminService(repos, hasher, bus);
            DemoData.seedIfEmpty(repos, hasher, accounts);
            manager = auth.login("admin", "admin123");
            teller = auth.login("teller1", "teller123");
            first = accounts.openAccount(teller, new OpenAccountRequest("savings", new BigDecimal("5000"), null, null,
                    new CustomerDetails("Test User", "Addr", "9999999999", "ID1")));
            customerId = first.customerId();
            savings = first.accountNo();
            login = first.loginId();
            tempPassword = first.tempPassword();
            current = accounts.openAccount(teller, new OpenAccountRequest("current", new BigDecimal("2000"),
                    new BigDecimal("1000"), customerId, null)).accountNo();
        }

        public BigDecimal bal(String no) {
            return accounts.details(manager, no).account().getBalance();
        }

        public List<TransactionType> types(String no) {
            return repos.transactions().latest(no, 100).stream().map(t -> t.type()).collect(Collectors.toList());
        }
    }

    public static void run() {
        System.out.println("== Business logic (TC1-TC9 and more) ==");
        World w = new World();

        test("TC1 deposit adds to balance and logs a ledger entry", () -> {
            World x = new World();
            x.accounts.deposit(x.teller, x.savings, new BigDecimal("500"));
            eqMoney("5500", x.bal(x.savings), "balance");
            eq(TransactionType.DEPOSIT, x.types(x.savings).get(0), "ledger entry");
        });

        test("TC2 withdraw within balance", () -> {
            World x = new World();
            x.accounts.withdraw(x.teller, x.savings, new BigDecimal("1000"));
            eqMoney("4000", x.bal(x.savings), "balance");
        });

        test("TC3 savings minimum balance blocks the withdrawal", () -> {
            World x = new World();
            throwsA(InsufficientFundsException.class,
                    () -> x.accounts.withdraw(x.teller, x.savings, new BigDecimal("4500")), "min balance");
            eqMoney("5000", x.bal(x.savings), "balance unchanged");
        });

        test("TC4 current account may overdraw up to its limit, not beyond", () -> {
            World x = new World();
            x.accounts.withdraw(x.teller, x.current, new BigDecimal("2500"));
            eqMoney("-500", x.bal(x.current), "overdrawn balance");
            throwsA(InsufficientFundsException.class,
                    () -> x.accounts.withdraw(x.teller, x.current, new BigDecimal("600")), "past overdraft");
        });

        test("TC5 transfer moves money and writes two ledger entries", () -> {
            World x = new World();
            x.accounts.transfer(x.teller, x.savings, x.current, new BigDecimal("1500"));
            eqMoney("3500", x.bal(x.savings), "source");
            eqMoney("3500", x.bal(x.current), "destination");
            eq(TransactionType.TRANSFER_OUT, x.types(x.savings).get(0), "out entry");
            eq(TransactionType.TRANSFER_IN, x.types(x.current).get(0), "in entry");
        });

        test("TC6 transfer with insufficient funds changes nothing", () -> {
            World x = new World();
            throwsA(InsufficientFundsException.class,
                    () -> x.accounts.transfer(x.teller, x.savings, x.current, new BigDecimal("4500")), "transfer");
            eqMoney("5000", x.bal(x.savings), "source");
            eqMoney("2000", x.bal(x.current), "destination");
        });

        test("TC7 transfer to an unknown account fails cleanly", () -> {
            World x = new World();
            throwsA(AccountNotFoundException.class,
                    () -> x.accounts.transfer(x.teller, x.savings, "9999999999", new BigDecimal("100")), "unknown");
            eqMoney("5000", x.bal(x.savings), "source unchanged");
        });

        test("TC7b transfer rolls back BOTH sides if the second step fails (frozen destination)", () -> {
            World x = new World();
            x.accounts.setFrozen(x.manager, x.current, true);
            throwsA(BankException.class,
                    () -> x.accounts.transfer(x.teller, x.savings, x.current, new BigDecimal("100")), "frozen");
            eqMoney("5000", x.bal(x.savings), "source rolled back");
        });

        test("TC8 closing needs a zero balance; closed accounts reject transactions", () -> {
            World x = new World();
            throwsA(BankException.class, () -> x.accounts.close(x.teller, x.savings), "close with balance");
            x.accounts.withdraw(x.teller, x.current, new BigDecimal("2000"));
            x.accounts.close(x.teller, x.current);
            eq(AccountStatus.CLOSED, x.accounts.details(x.manager, x.current).account().getStatus(), "status");
            throwsA(BankException.class, () -> x.accounts.deposit(x.teller, x.current, BigDecimal.TEN), "deposit closed");
        });

        test("TC9 three wrong passwords lock the user; a manager unlocks", () -> {
            World x = new World();
            for (int i = 0; i < 3; i++) {
                throwsA(InvalidLoginException.class, () -> x.auth.login(x.login, "wrong"), "bad password");
            }
            throwsA(InvalidLoginException.class, () -> x.auth.login(x.login, x.tempPassword), "locked even with right password");
            long uid = x.repos.users().findByUserId(x.login).get().getId();
            x.admin.unlock(x.manager, uid);
            eq(x.login, x.auth.login(x.login, x.tempPassword).getUserId(), "login after unlock");
        });

        test("passwords are stored hashed and salted", () -> {
            String h = w.repos.users().findByUserId("admin").get().getPasswordHash();
            check(!h.contains("admin123"), "plain text stored");
            check(h.startsWith("pbkdf2$"), "unexpected format");
            check(!w.hasher.hash("same").equals(w.hasher.hash("same")), "salt must differ");
            check(w.hasher.verify("admin123", h) && !w.hasher.verify("nope", h), "verify");
        });

        test("invalid amounts are rejected", () -> {
            World x = new World();
            for (String bad : new String[]{"0", "-5", "10000000.01"}) {
                throwsA(BankException.class, () -> x.accounts.deposit(x.teller, x.savings, new BigDecimal(bad)), bad);
            }
        });

        test("savings need the minimum opening deposit", () -> {
            World x = new World();
            throwsA(BankException.class, () -> x.accounts.openAccount(x.teller,
                    new OpenAccountRequest("savings", new BigDecimal("500"), null, x.customerId, null)), "opening");
        });

        test("loan life-cycle: apply, approve (disburse), repay, close", () -> {
            World x = new World();
            User cust = x.auth.login("alice", "alice123");
            String acc = x.accounts.accountsOf(cust, cust.customerId()).get(0).getAccountNo();
            BigDecimal before = x.bal(acc);
            LoanService.Application app = x.loans.apply(cust, acc, new BigDecimal("12000"), 12, "Bike");
            eqMoney("1054.99", app.emi(), "EMI at 10% over 12 months");
            throwsA(BankException.class, () -> x.accounts.close(x.teller, acc), "pending loan blocks closing");
            x.loans.decide(x.manager, app.id(), true);
            eqMoney(before.add(new BigDecimal("12000")).toPlainString(), x.bal(acc), "disbursed");
            Loan l = x.loans.myLoans(cust).get(0);
            eq(LoanStatus.DISBURSED, l.getStatus(), "status");
            BigDecimal left = x.loans.repay(cust, l.getId(), acc, l.getOutstanding());
            eqMoney("0", left, "outstanding");
            eq(LoanStatus.CLOSED, x.loans.myLoans(cust).get(0).getStatus(), "closed");
            throwsA(BankException.class, () -> x.loans.decide(x.manager, app.id(), true), "already decided");
        });

        test("loan rejection leaves the balance alone", () -> {
            World x = new World();
            User cust = x.auth.login("alice", "alice123");
            String acc = x.accounts.accountsOf(cust, cust.customerId()).get(0).getAccountNo();
            BigDecimal before = x.bal(acc);
            long id = x.loans.apply(cust, acc, new BigDecimal("5000"), 6, "Fees").id();
            x.loans.decide(x.manager, id, false);
            eqMoney(before.toPlainString(), x.bal(acc), "unchanged");
            eq(LoanStatus.REJECTED, x.loans.myLoans(cust).get(0).getStatus(), "status");
        });

        test("monthly interest is credited to savings only", () -> {
            World x = new World();
            BigDecimal curBefore = x.bal(x.current);
            x.accounts.creditMonthlyInterest(x.manager);
            eq(TransactionType.INTEREST, x.types(x.savings).get(0), "savings got interest");
            eqMoney("5016.67", x.bal(x.savings), "5000 at 4% for a month");
            eqMoney(curBefore.toPlainString(), x.bal(x.current), "current untouched");
        });

        test("roles: polymorphic permissions and access control", () -> {
            World x = new World();
            User cust = x.auth.login("alice", "alice123");
            check(x.manager.can(Permission.APPROVE_LOAN) && !x.teller.can(Permission.APPROVE_LOAN), "approve");
            check(x.manager.can(Permission.OPEN_ACCOUNT), "manager inherits teller powers");
            check(cust.permissions().size() == 1 && cust.can(Permission.SELF_SERVICE), "customer");
            throwsA(AccessDeniedException.class, () -> x.accounts.creditMonthlyInterest(x.teller), "teller interest");
            throwsA(AccessDeniedException.class, () -> x.admin.auditTrail(cust), "customer audit");
            throwsA(AccessDeniedException.class, () -> x.accounts.details(cust, x.savings), "other's account");
        });

        test("polymorphism: each account type applies its own withdrawal rule", () -> {
            World x = new World();
            Account s = x.accounts.details(x.manager, x.savings).account();
            Account c = x.accounts.details(x.manager, x.current).account();
            BigDecimal amt = new BigDecimal("2500");
            check(s.canWithdraw(amt) && c.canWithdraw(amt), "both can at 2500");
            check(!s.canWithdraw(new BigDecimal("4500")) && c.canWithdraw(new BigDecimal("2900")), "rules differ");
        });

        test("audit trail records critical actions", () -> {
            World x = new World();
            x.accounts.deposit(x.teller, x.savings, BigDecimal.TEN);
            check(x.admin.auditTrail(x.manager).stream().anyMatch(e -> e.action().equals("DEPOSIT")), "deposit audited");
            check(x.admin.auditTrail(x.manager).stream().anyMatch(e -> e.action().equals("LOGIN")), "login audited");
        });

        test("concurrent transfers never lose or create money", () -> {
            World x = new World();
            List<Thread> ts = new ArrayList<>();
            List<Throwable> errors = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final boolean forward = i % 2 == 0;
                Thread t = new Thread(() -> {
                    for (int k = 0; k < 40; k++) {
                        try {
                            if (forward) x.accounts.transfer(x.teller, x.savings, x.current, BigDecimal.TEN);
                            else x.accounts.transfer(x.teller, x.current, x.savings, BigDecimal.TEN);
                        } catch (BankException ignored) {
                            // insufficient funds is a legal outcome
                        } catch (Throwable e) {
                            synchronized (errors) { errors.add(e); }
                        }
                    }
                });
                ts.add(t);
                t.start();
            }
            for (Thread t : ts) {
                try { t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            }
            check(errors.isEmpty(), "unexpected errors " + errors);
            eqMoney("7000", x.bal(x.savings).add(x.bal(x.current)), "total");
        });
    }
}
