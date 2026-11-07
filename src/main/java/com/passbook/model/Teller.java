package com.passbook.model;

import java.util.EnumSet;
import java.util.Set;

public class Teller extends User {
    public Teller(long id, String userId, String fullName, String passwordHash, int failed, boolean locked,
                  boolean active) {
        super(id, userId, fullName, passwordHash, failed, locked, active);
    }

    @Override public Role role() { return Role.TELLER; }

    @Override
    public Set<Permission> permissions() {
        return EnumSet.of(Permission.SERVE_ANY_ACCOUNT, Permission.OPEN_ACCOUNT,
                Permission.MANAGE_CUSTOMERS, Permission.CLOSE_ACCOUNT);
    }

    @Override
    public User copy() {
        return new Teller(getId(), getUserId(), getFullName(), getPasswordHash(), getFailedAttempts(),
                isLocked(), isActive());
    }
}
