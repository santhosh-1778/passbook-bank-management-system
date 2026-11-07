package com.passbook.model;

import java.util.EnumSet;
import java.util.Set;

/** INHERITANCE + POLYMORPHISM: a manager is a teller with extra powers, so permissions() extends the parent's. */
public class Manager extends Teller {
    public Manager(long id, String userId, String fullName, String passwordHash, int failed, boolean locked,
                   boolean active) {
        super(id, userId, fullName, passwordHash, failed, locked, active);
    }

    @Override public Role role() { return Role.MANAGER; }

    @Override
    public Set<Permission> permissions() {
        Set<Permission> p = EnumSet.copyOf(super.permissions());
        p.addAll(EnumSet.of(Permission.FREEZE_ACCOUNT, Permission.APPROVE_LOAN, Permission.MANAGE_STAFF,
                Permission.CREDIT_INTEREST, Permission.VIEW_AUDIT));
        return p;
    }

    @Override
    public User copy() {
        return new Manager(getId(), getUserId(), getFullName(), getPasswordHash(), getFailedAttempts(),
                isLocked(), isActive());
    }
}
