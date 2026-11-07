package com.passbook.web;

import com.passbook.model.User;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Request {
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> form;
    private final Map<String, String> cookies;
    private Map<String, String> params = Map.of();
    private Session session;
    private User user;

    private Request(String method, String path, Map<String, String> query, Map<String, String> form,
                    Map<String, String> cookies) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.form = form;
        this.cookies = cookies;
    }

    static Request parse(HttpExchange ex) throws IOException {
        Map<String, String> form = new HashMap<>();
        if ("POST".equals(ex.getRequestMethod())) {
            byte[] body = ex.getRequestBody().readNBytes(65_536);
            form = parseUrlEncoded(new String(body, StandardCharsets.UTF_8));
        }
        Map<String, String> cookies = new HashMap<>();
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers != null) {
            for (String h : headers) {
                for (String part : h.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) cookies.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
        }
        return new Request(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                parseUrlEncoded(ex.getRequestURI().getRawQuery()), form, cookies);
    }

    private static Map<String, String> parseUrlEncoded(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String pair : s.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    public String method() { return method; }
    public String path() { return path; }
    public String cookie(String name) { return cookies.get(name); }

    /** Trimmed form field, "" if absent. */
    public String form(String name) { return form.getOrDefault(name, "").trim(); }

    /** All submitted fields, to refill a form after an error. */
    public Map<String, String> formData() { return form; }

    public String query(String name) { return query.getOrDefault(name, "").trim(); }
    public String param(String name) { return params.get(name); }

    void setParams(Map<String, String> p) { params = p; }

    public Session session() { return session; }
    public void setSession(Session s) { session = s; }
    public User user() { return user; }
    void setUser(User u) { user = u; }

    /** Path variable as a number, or -1 if it is not one. */
    public long paramLong(String name) {
        try {
            return Long.parseLong(params.get(name));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
