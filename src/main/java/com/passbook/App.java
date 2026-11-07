package com.passbook;

import com.passbook.repo.Repositories;
import com.passbook.repo.sqlite.SqliteRepositories;
import com.passbook.security.PasswordHasher;
import com.passbook.security.Pbkdf2PasswordHasher;
import com.passbook.service.AccountService;
import com.passbook.service.AdminService;
import com.passbook.service.AuditListener;
import com.passbook.service.AuthService;
import com.passbook.service.DemoData;
import com.passbook.service.EventBus;
import com.passbook.service.LoanService;
import com.passbook.web.AccountController;
import com.passbook.web.AdminController;
import com.passbook.web.AuthController;
import com.passbook.web.BankServer;
import com.passbook.web.Controller;
import com.passbook.web.DashboardController;
import com.passbook.web.LoanController;
import com.passbook.web.Router;
import com.passbook.web.SessionStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Composition root: builds every object once and connects them (dependency injection by hand). */
public final class App {
    private App() {
    }

    /** Wires the whole application on top of the given repositories. */
    public static BankServer build(Repositories repos, Path staticDir) {
        PasswordHasher hasher = new Pbkdf2PasswordHasher();
        EventBus bus = new EventBus();
        bus.subscribe(new AuditListener(repos.audit()));  // Observer: the audit log listens to everything

        AuthService auth = new AuthService(repos, hasher, bus);
        AccountService accounts = new AccountService(repos, hasher, bus);
        LoanService loans = new LoanService(repos, bus);
        AdminService admin = new AdminService(repos, hasher, bus);
        DemoData.seedIfEmpty(repos, hasher, accounts);

        SessionStore sessions = new SessionStore(10);
        Router router = new Router();
        List<Controller> controllers = List.of(
                new AuthController(auth, sessions),
                new DashboardController(accounts, loans, admin),
                new AccountController(accounts),
                new LoanController(loans, accounts),
                new AdminController(admin, accounts));
        for (Controller c : controllers) c.register(router);  // polymorphism: each registers its own routes
        return new BankServer(router, sessions, auth, staticDir);
    }

    public static void main(String[] args) throws IOException {
        String db = System.getenv().getOrDefault("BANK_DB", "bank.db");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        BankServer server = build(SqliteRepositories.open(db), Path.of("static"));
        int actual = server.start("127.0.0.1", port);
        System.out.println("Passbook is running at http://127.0.0.1:" + actual + "  (database: " + db + ")");
        System.out.println("Press Ctrl+C to stop.");
    }
}
