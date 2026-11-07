package com.passbook.web;

import java.nio.charset.StandardCharsets;

public final class Response {
    private final int status;
    private final String contentType;
    private final byte[] body;
    private final String location;

    private Response(int status, String contentType, byte[] body, String location) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
        this.location = location;
    }

    public static Response html(String html) {
        return html(200, html);
    }

    public static Response html(int status, String html) {
        return new Response(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), null);
    }

    public static Response redirect(String location) {
        return new Response(302, "text/plain", new byte[0], location);
    }

    static Response bytes(String contentType, byte[] data) {
        return new Response(200, contentType, data, null);
    }

    static Response empty(int status) {
        return new Response(status, "text/plain", new byte[0], null);
    }

    int status() { return status; }
    String contentType() { return contentType; }
    byte[] body() { return body; }
    String location() { return location; }
}
