package com.passbook.service;

import com.passbook.model.AuditEntry;
import com.passbook.repo.AuditRepository;

/** Writes every event to the audit table (runs inside the caller's transaction). */
public class AuditListener implements BankListener {
    private final AuditRepository audit;

    public AuditListener(AuditRepository audit) {
        this.audit = audit;
    }

    @Override
    public void onEvent(BankEvent e) {
        audit.append(new AuditEntry(0, e.actor(), e.action(), e.detail(), e.at()));
    }
}
