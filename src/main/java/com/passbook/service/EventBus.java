package com.passbook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The subject in the Observer pattern. Services publish; listeners (audit log, ...) react. */
public class EventBus {
    private final List<BankListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(BankListener listener) {
        listeners.add(listener);
    }

    public void publish(String actor, String action, String detail) {
        BankEvent e = new BankEvent(actor, action, detail, LocalDateTime.now().withNano(0));
        for (BankListener l : listeners) l.onEvent(e);
    }
}
