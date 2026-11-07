package com.passbook.repo.sqlite;

import com.passbook.model.Loan;
import com.passbook.model.LoanStatus;
import com.passbook.repo.LoanRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

class SqliteLoanRepository implements LoanRepository {
    private static final String COLS =
            "SELECT id, customer_id, account_no, principal, rate, tenure_months, purpose, emi, outstanding, status, applied_at, decided_by, decided_at FROM loans";

    private final Database db;

    SqliteLoanRepository(Database db) {
        this.db = db;
    }

    private static Loan map(ResultSet rs) throws SQLException {
        return new Loan(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("account_no"),
                Database.money(rs, "principal"), rs.getDouble("rate"), rs.getInt("tenure_months"),
                rs.getString("purpose"), Database.money(rs, "emi"), Database.money(rs, "outstanding"),
                LoanStatus.fromLabel(rs.getString("status")), Database.time(rs, "applied_at"),
                rs.getString("decided_by"), Database.time(rs, "decided_at"));
    }

    @Override
    public void insert(Loan l) {
        long id = db.insert("INSERT INTO loans(customer_id, account_no, principal, rate, tenure_months, purpose, emi, outstanding, status, applied_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                l.getCustomerId(), l.getAccountNo(), l.getPrincipal(), l.getRate(), l.getTenureMonths(),
                l.getPurpose(), l.getEmi(), l.getOutstanding(), l.getStatus().label(), l.getAppliedAt());
        l.assignId(id);
    }

    @Override
    public void update(Loan l) {
        db.update("UPDATE loans SET outstanding = ?, status = ?, decided_by = ?, decided_at = ? WHERE id = ?",
                l.getOutstanding(), l.getStatus().label(), l.getDecidedBy(), l.getDecidedAt(), l.getId());
    }

    @Override
    public Optional<Loan> findById(long id) {
        return db.queryOne(COLS + " WHERE id = ?", SqliteLoanRepository::map, id);
    }

    @Override
    public List<Loan> findByCustomer(long customerId) {
        return db.query(COLS + " WHERE customer_id = ? ORDER BY id DESC", SqliteLoanRepository::map, customerId);
    }

    @Override
    public List<Loan> findAll() {
        return db.query(COLS + " ORDER BY id DESC", SqliteLoanRepository::map);
    }

    @Override
    public boolean hasActiveLoanOn(String accountNo) {
        return db.queryOne("SELECT COUNT(*) FROM loans WHERE account_no = ? AND status IN ('Applied','Disbursed')",
                rs -> rs.getInt(1), accountNo).orElse(0) > 0;
    }
}
