package com.passbook.repo.sqlite;

import com.passbook.model.Role;
import com.passbook.model.User;
import com.passbook.model.UserFactory;
import com.passbook.repo.UserRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

class SqliteUserRepository implements UserRepository {
    private static final String COLS =
            "SELECT id, user_id, full_name, password_hash, role, customer_id, failed_attempts, locked, active FROM users";

    private final Database db;

    SqliteUserRepository(Database db) {
        this.db = db;
    }

    private static User map(ResultSet rs) throws SQLException {
        long cid = rs.getLong("customer_id");
        Long customerId = rs.wasNull() ? null : cid;
        return UserFactory.restore(Role.fromLabel(rs.getString("role")), rs.getLong("id"), rs.getString("user_id"),
                rs.getString("full_name"), rs.getString("password_hash"), rs.getInt("failed_attempts"),
                rs.getInt("locked") == 1, rs.getInt("active") == 1, customerId);
    }

    @Override
    public Optional<User> findById(long id) {
        return db.queryOne(COLS + " WHERE id = ?", SqliteUserRepository::map, id);
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        return db.queryOne(COLS + " WHERE user_id = ?", SqliteUserRepository::map, userId);
    }

    @Override
    public Optional<User> findByCustomerId(long customerId) {
        return db.queryOne(COLS + " WHERE customer_id = ?", SqliteUserRepository::map, customerId);
    }

    @Override
    public void insert(User u) {
        long id = db.insert(
                "INSERT INTO users(user_id, full_name, password_hash, role, customer_id, failed_attempts, locked, active) VALUES(?,?,?,?,?,?,?,?)",
                u.getUserId(), u.getFullName(), u.getPasswordHash(), u.role().label(), u.customerId(),
                u.getFailedAttempts(), u.isLocked(), u.isActive());
        u.assignId(id);
    }

    @Override
    public void update(User u) {
        db.update("UPDATE users SET full_name = ?, failed_attempts = ?, locked = ?, active = ? WHERE id = ?",
                u.getFullName(), u.getFailedAttempts(), u.isLocked(), u.isActive(), u.getId());
    }

    @Override
    public List<User> findEmployees() {
        return db.query(COLS + " WHERE role != 'customer' ORDER BY role DESC, user_id", SqliteUserRepository::map);
    }

    @Override
    public List<User> findLocked() {
        return db.query(COLS + " WHERE locked = 1 ORDER BY user_id", SqliteUserRepository::map);
    }

    @Override
    public boolean isEmpty() {
        return db.queryOne("SELECT COUNT(*) FROM users", rs -> rs.getInt(1)).orElse(0) == 0;
    }
}
