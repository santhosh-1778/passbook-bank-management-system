package com.passbook.service;

/** OBSERVER pattern: anything that wants to hear about bank events implements this. */
public interface BankListener {
    void onEvent(BankEvent event);
}
