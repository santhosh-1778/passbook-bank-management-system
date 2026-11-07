package com.passbook.service;

import com.passbook.exception.InvalidLoginException;
import com.passbook.model.User;
import com.passbook.repo.Repositories;
import com.passbook.repo.UnitOfWork;
import com.passbook.repo.UserRepository;
import com.passbook.security.PasswordHasher;

import java.util.Optional;

public class AuthService {
    public static final int MAX_FAILED_LOGINS = 3;
    private static final String BAD = "Invalid user ID or password.";

    private final UserRepository users;
    private final UnitOfWork uow;
    private final PasswordHasher hasher;
    private final EventBus bus;

    public AuthService(Repositories repos, PasswordHasher hasher, EventBus bus) {
        this.users = repos.users();
        this.uow = repos.uow();
        this.hasher = hasher;
        this.bus = bus;
    }

    private record Attempt(User user, String error) {
    }

    /**
     * FR17: authenticate. A failure must be saved (the counter), so the attempt is committed first and
     * the exception is thrown afterwards.
     */
    public User login(String userId, String password) {
        String id = userId == null ? "" : userId.trim();
        String pw = password == null ? "" : password;
        Attempt a = uow.call(() -> attempt(id, pw));
        if (a.user() != null) return a.user();
        throw new InvalidLoginException(a.error());
    }

    private Attempt attempt(String id, String pw) {
        Optional<User> found = users.findByUserId(id);
        if (found.isEmpty()) return new Attempt(null, BAD);
        User u = found.get();
        if (u.isLocked()) {
            return new Attempt(null, "This user is locked after 3 failed attempts. Ask a manager to unlock it.");
        }
        if (!u.isActive()) {
            return new Attempt(null, "This user has been disabled.");
        }
        if (hasher.verify(pw, u.getPasswordHash())) {
            u.resetFailedAttempts();
            users.update(u);
            bus.publish(id, "LOGIN", "");
            return new Attempt(u, null);
        }
        boolean nowLocked = u.registerFailedAttempt(MAX_FAILED_LOGINS);
        users.update(u);
        bus.publish(id, "LOGIN_FAILED", "attempt " + u.getFailedAttempts());
        return new Attempt(null, nowLocked
                ? "Too many failed attempts. The user is now locked; ask a manager to unlock it."
                : BAD);
    }

    public Optional<User> find(long id) {
        return users.findById(id);
    }
}
