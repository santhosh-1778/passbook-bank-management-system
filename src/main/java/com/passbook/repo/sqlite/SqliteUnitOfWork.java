package com.passbook.repo.sqlite;

import com.passbook.repo.UnitOfWork;

import java.util.function.Supplier;

class SqliteUnitOfWork implements UnitOfWork {
    private final Database db;

    SqliteUnitOfWork(Database db) {
        this.db = db;
    }

    @Override
    public <T> T call(Supplier<T> work) {
        return db.inTransaction(work);
    }
}
