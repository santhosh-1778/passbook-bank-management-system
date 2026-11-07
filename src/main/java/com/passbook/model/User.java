package com.passbook.model;

import com.passbook.exception.AccessDeniedException;

import java.util.Set;

/**
 * ABSTRACTION: a login identity. Subclasses (Customer, Teller, Manager) decide their role and
 * permissions. Login-lockout state is encapsulated here and changed only through methods.
 */
public abstract class User {
    private long id;
    private final String userId;
    private String fullName;
    private final String passwordHash;
    private int failedAttempts;
    private boolean locked;
    private boolean active;

    protected User(long id, String userId, String fullName, String passwordHash,
                   int failedAttempts, boolean locked, boolean active) {
        this.id = id;
        this.userId = userId;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.failedAttempts = failedAttempts;
        this.locked = locked;
        this.active = active;
    }

    public abstract Role role();

    public abstract Set<Permission> permissions();

    /** Only customers are linked to a customer profile. */
    public Long customerId() {
        return null;
    }

    public final boolean can(Permission p) {
        return permissions().contains(p);
    }

    public final void require(Permission p) {
        if (!can(p)) {
            throw new AccessDeniedException("Your role (" + role().label() + ") may not do this.");
        }
    }

    /** Returns true if this failure locked the user. */
    public boolean registerFailedAttempt(int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) locked = true;
        return locked;
    }

    public void resetFailedAttempts() { failedAttempts = 0; }

    public void unlock() {
        locked = false;
        failedAttempts = 0;
    }

    public void setActive(boolean active) { this.active = active; }
    public void rename(String newName) { this.fullName = newName; }

    public void assignId(long newId) {
        if (id != 0) throw new IllegalStateException("id already assigned");
        id = newId;
    }

    public long getId() { return id; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isLocked() { return locked; }
    public boolean isActive() { return active; }

    public abstract User copy();
}
