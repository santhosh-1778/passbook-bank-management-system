package com.passbook.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** Holds live sessions. Idle sessions expire (NFR: session timeout). */
public final class SessionStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long idleMillis;

    public SessionStore(long idleMinutes) {
        this.idleMillis = idleMinutes * 60_000L;
    }

    /** Returns the live session for this id, or null if unknown or expired. */
    public Session find(String id) {
        if (id == null) return null;
        Session s = sessions.get(id);
        if (s == null) return null;
        if (System.currentTimeMillis() - s.lastAccess() > idleMillis) {
            sessions.remove(id);
            return null;
        }
        s.touch();
        return s;
    }

    public Session create() {
        sweep();
        return store(null);
    }

    /** New session id after login (prevents session fixation). */
    public Session login(Session old, long userId) {
        if (old != null) sessions.remove(old.id());
        return store(userId);
    }

    public void destroy(Session s) {
        if (s != null) sessions.remove(s.id());
    }

    private Session store(Long userId) {
        Session s = new Session(token(32), token(16), userId);
        sessions.put(s.id(), s);
        return s;
    }

    private String token(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.lastAccess() > idleMillis);
    }
}
