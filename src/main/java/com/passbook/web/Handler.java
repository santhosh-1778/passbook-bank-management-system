package com.passbook.web;

@FunctionalInterface
public interface Handler {
    Response handle(Request request);
}
