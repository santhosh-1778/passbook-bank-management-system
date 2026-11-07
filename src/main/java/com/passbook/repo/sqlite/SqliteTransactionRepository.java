package com.passbook.repo.sqlite;

import com.passbook.model.Transaction;
import com.passbook.model.TransactionType;
import com.passbook.repo.TransactionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

class SqliteTransactionRepository implements TransactionRepository {
    private static final String COLS =
            "SELECT id, account_no, type, amount, balance_after, note, created_at FROM transactions";

    private final Database db;

    SqliteTransactionRepository(Database db) {
        this.db = db;
    }

    private static Transaction map(ResultSet rs) throws SQLException {
        return new Transaction(rs.getLong("id"), rs.getString("account_no"),
                TransactionType.valueOf(rs.getString("type")), Database.money(rs, "amount"),
                Database.money(rs, "balance_after"), rs.getString("note"), Database.time(rs, "created_at"));
    }

    @Override
    public void insert(Transaction t) {
        db.insert("INSERT INTO transactions(account_no, type, amount, balance_after, note, created_at) VALUES(?,?,?,?,?,?)",
                t.accountNo(), t.type().name(), t.amount(), t.balanceAfter(), t.note(), t.createdAt());
    }

    @Override
    public List<Transaction> latest(String accountNo, int n) {
        return db.query(COLS + " WHERE account_no = ? ORDER BY id DESC LIMIT ?", SqliteTransactionRepository::map,
                accountNo, n);
    }

    @Override
    public List<Transaction> between(String accountNo, LocalDate from, LocalDate to) {
        return db.query(COLS + " WHERE account_no = ? AND created_at >= ? AND created_at <= ? ORDER BY id",
                SqliteTransactionRepository::map, accountNo, from + " 00:00:00", to + " 23:59:59");
    }

    @Override
    public List<Transaction> recent(int n) {
        return db.query(COLS + " ORDER BY id DESC LIMIT ?", SqliteTransactionRepository::map, n);
    }
}
