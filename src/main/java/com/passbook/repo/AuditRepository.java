package com.passbook.repo;

import com.passbook.model.AuditEntry;

import java.util.List;

public interface AuditRepository {
    void append(AuditEntry entry);

    List<AuditEntry> latest(int n);
}
