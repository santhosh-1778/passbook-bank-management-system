package com.passbook.repo;

import java.util.function.Supplier;

/** Runs a block atomically: everything inside commits together or rolls back together. */
public interface UnitOfWork {
    <T> T call(Supplier<T> work);

    default void execute(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }
}
