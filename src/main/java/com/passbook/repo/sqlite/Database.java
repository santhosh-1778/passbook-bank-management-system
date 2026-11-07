package com.passbook.repo.sqlite;

import com.passbook.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Owns the single SQLite connection. A re-entrant lock serialises all access, so two web requests can
 * never interleave inside one transaction (this is what keeps balances consistent). Repositories use only
 * the small helpers below, so JDBC details live in one place.
 */
final class Database {
    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Connection connection;
    private int depth;

    Database(String path) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 10000");
                st.execute("PRAGMA journal_mode = WAL");
                for (String ddl : Schema.STATEMENTS) st.execute(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open the SQLite database at " + path
                    + " (is the sqlite-jdbc jar on the classpath?): " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ transactions
    <T> T inTransaction(Supplier<T> work) {
        lock.lock();
        try {
            if (depth > 0) {  // nested call: join the outer transaction
                return work.get();
            }
            depth++;
            try {
                connection.setAutoCommit(false);
                T result = work.get();
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("Database error: " + e.getMessage(), e);
            } catch (RuntimeException | Error e) {
                rollbackQuietly();
                throw e;
            } finally {
                depth--;
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // nothing more to do
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original failure matters more
        }
    }

    // ------------------------------------------------------------------ helpers
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    <T> java.util.Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = query(sql, mapper, args);
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }

    int update(String sql, Object... args) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /** Runs an INSERT and returns the new row id. */
    long insert(String sql, Object... args) {
        lock.lock();
        try {
            update(sql, args);
            return queryOne("SELECT last_insert_rowid()", rs -> rs.getLong(1)).orElseThrow();
        } finally {
            lock.unlock();
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int pos = i + 1;
            if (a == null) ps.setObject(pos, null);
            else if (a instanceof BigDecimal) ps.setDouble(pos, ((BigDecimal) a).doubleValue());
            else if (a instanceof LocalDateTime) ps.setString(pos, ((LocalDateTime) a).format(TS));
            else if (a instanceof LocalDate) ps.setString(pos, a.toString());
            else if (a instanceof Boolean) ps.setInt(pos, ((Boolean) a) ? 1 : 0);
            else ps.setObject(pos, a);
        }
    }

    // ------------------------------------------------------------------ column readers
    static BigDecimal money(ResultSet rs, String col) throws SQLException {
        return Money.ofDouble(rs.getDouble(col));
    }

    static BigDecimal moneyOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : Money.ofDouble(v);
    }

    static LocalDateTime time(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        return s == null ? null : LocalDateTime.parse(s, TS);
    }
}
