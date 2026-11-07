package com.passbook.repo.sqlite;

import com.passbook.model.AuditEntry;
import com.passbook.repo.AuditRepository;

import java.util.List;

class SqliteAuditRepository implements AuditRepository {
    private final Database db;

    SqliteAuditRepository(Database db) {
        this.db = db;
    }

    @Override
    public void append(AuditEntry e) {
        db.insert("INSERT INTO audit_log(actor, action, detail, created_at) VALUES(?,?,?,?)",
                e.actor(), e.action(), e.detail(), e.createdAt());
    }

    @Override
    public List<AuditEntry> latest(int n) {
        return db.query("SELECT id, actor, action, detail, created_at FROM audit_log ORDER BY id DESC LIMIT ?",
                rs -> new AuditEntry(rs.getLong("id"), rs.getString("actor"), rs.getString("action"),
                        rs.getString("detail"), Database.time(rs, "created_at")), n);
    }
}
