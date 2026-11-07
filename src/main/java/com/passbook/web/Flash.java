package com.passbook.web;

/** A one-time message shown on the next page. kind is "ok", "err" or "key". */
public record Flash(String kind, String message) {
}
