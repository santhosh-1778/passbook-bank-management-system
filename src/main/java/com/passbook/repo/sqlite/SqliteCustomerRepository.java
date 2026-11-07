package com.passbook.repo.sqlite;

import com.passbook.model.CustomerProfile;
import com.passbook.repo.CustomerRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

class SqliteCustomerRepository implements CustomerRepository {
    private static final String COLS = "SELECT id, name, address, phone, id_proof, created_at FROM customers";

    private final Database db;

    SqliteCustomerRepository(Database db) {
        this.db = db;
    }

    private static CustomerProfile map(ResultSet rs) throws SQLException {
        return new CustomerProfile(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                rs.getString("phone"), rs.getString("id_proof"), Database.time(rs, "created_at"));
    }

    @Override
    public CustomerProfile insert(CustomerProfile p) {
        long id = db.insert("INSERT INTO customers(name, address, phone, id_proof, created_at) VALUES(?,?,?,?,?)",
                p.name(), p.address(), p.phone(), p.idProof(), p.createdAt());
        return p.withId(id);
    }

    @Override
    public void update(CustomerProfile p) {
        db.update("UPDATE customers SET name = ?, address = ?, phone = ?, id_proof = ? WHERE id = ?",
                p.name(), p.address(), p.phone(), p.idProof(), p.id());
    }

    @Override
    public Optional<CustomerProfile> findById(long id) {
        return db.queryOne(COLS + " WHERE id = ?", SqliteCustomerRepository::map, id);
    }

    @Override
    public List<CustomerProfile> search(String text) {
        String like = "%" + text + "%";
        return db.query(COLS + " WHERE name LIKE ? OR phone LIKE ? OR CAST(id AS TEXT) = ? ORDER BY name",
                SqliteCustomerRepository::map, like, like, text);
    }

    @Override
    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM customers", rs -> rs.getInt(1)).orElse(0);
    }
}
