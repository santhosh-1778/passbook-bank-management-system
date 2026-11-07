package com.passbook.web;

import java.util.ArrayList;
import java.util.List;

/** Server-side session: who is logged in, the CSRF token, and pending flash messages. */
public final class Session {
    private final String id;
    private final String csrf;
    private final Long userId;
    private final List<Flash> flashes = new ArrayList<>();
    private volatile long lastAccess = System.currentTimeMillis();

    Session(String id, String csrf, Long userId) {
        this.id = id;
        this.csrf = csrf;
        this.userId = userId;
    }

    public String id() { return id; }
    public String csrf() { return csrf; }
    public Long userId() { return userId; }
    long lastAccess() { return lastAccess; }
    void touch() { lastAccess = System.currentTimeMillis(); }

    public synchronized void flash(String kind, String message) {
        flashes.add(new Flash(kind, message));
    }

    public synchronized List<Flash> drainFlashes() {
        List<Flash> copy = new ArrayList<>(flashes);
        flashes.clear();
        return copy;
    }
}
