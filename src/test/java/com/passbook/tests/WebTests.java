package com.passbook.tests;

import com.passbook.App;
import com.passbook.testsupport.MemoryRepositories;
import com.passbook.web.BankServer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.passbook.tests.T.check;
import static com.passbook.tests.T.eq;
import static com.passbook.tests.T.test;

/** Drives the real HTTP server (on the in-memory store) like a browser: logins, CSRF, roles, main flows. */
public final class WebTests {
    private WebTests() {
    }

    record Res(int status, String body, String location, String contentType) {
    }

    /** A minimal browser: keeps its own cookie and never follows redirects by itself. */
    static final class Browser {
        private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        private final String base;
        private String sid;
        private boolean loggedIn;

        Browser(String base) {
            this.base = base;
        }

        Res get(String path) {
            return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
        }

        Res post(String path, Map<String, String> form) {
            String body = form.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            return send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
        }

        /** POST with a valid CSRF token, as a real form would send. */
        Res submit(String path, Map<String, String> form) {
            Map<String, String> f = new java.util.HashMap<>(form);
            f.put("_csrf", token());
            return post(path, f);
        }

        /** Follows redirects, like a browser. */
        Res follow(Res r) {
            int hops = 0;
            while (r.status() == 302 && hops++ < 5) r = get(r.location());
            return r;
        }

        String token() {
            Res page = get(loggedIn ? "/" : "/login");
            Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
            check(m.find(), "no CSRF token on page (status " + page.status() + ")");
            return m.group(1);
        }

        Res login(String user, String pw) {
            Res r = submit("/login", Map.of("user_id", user, "password", pw));
            if (r.status() == 302) loggedIn = true;
            return r;
        }

        private Res send(HttpRequest.Builder b) {
            try {
                if (sid != null) b.header("Cookie", "SID=" + sid);
                HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                r.headers().allValues("Set-Cookie").forEach(c -> {
                    if (c.startsWith("SID=")) sid = c.substring(4, c.indexOf(';'));
                });
                return new Res(r.statusCode(), r.body(), r.headers().firstValue("Location").orElse(null),
                        r.headers().firstValue("Content-Type").orElse(""));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void run() {
        System.out.println("== Web layer (real HTTP server, in-memory store) ==");
        BankServer server = App.build(new MemoryRepositories().create(), Path.of("static"));
        try {
            int port = server.start("127.0.0.1", 0);
            String base = "http://127.0.0.1:" + port;

            test("anonymous visitors are sent to the login page", () -> {
                Browser b = new Browser(base);
                Res r = b.get("/");
                eq(302, r.status(), "status");
                check(r.location().startsWith("/login"), "redirect to login, was " + r.location());
                Res login = b.get("/login");
                check(login.status() == 200 && login.body().contains("Sign in"), "login page");
            });

            test("static CSS is served, and path traversal is blocked", () -> {
                Browser b = new Browser(base);
                Res css = b.get("/static/style.css");
                check(css.status() == 200 && css.contentType().startsWith("text/css"), "css " + css.status());
                check(b.get("/static/..%2fpom.xml").status() == 404, "traversal must 404");
            });

            test("wrong password shows an error; open redirect is blocked", () -> {
                Browser b = new Browser(base);
                Res bad = b.submit("/login", Map.of("user_id", "alice", "password", "nope"));
                check(bad.body().contains("Invalid user ID or password"), "error text");
                Browser c = new Browser(base);
                Res ok = c.submit("/login?next=//evil.example", Map.of("user_id", "alice", "password", "alice123"));
                eq(302, ok.status(), "login redirect");
                eq("/", ok.location(), "next=//evil must be ignored");
            });

            test("POST without a CSRF token is refused", () -> {
                Browser b = new Browser(base);
                b.login("alice", "alice123");
                eq(400, b.post("/transfer", Map.of("from_no", "x", "to_no", "y", "amount", "1")).status(), "forged post");
            });

            test("customer: dashboard, transfer, errors, statement", () -> {
                Browser b = new Browser(base);
                eq(302, b.login("alice", "alice123").status(), "login");
                Res home = b.get("/");
                check(home.body().contains("Alice Verma") && home.body().contains("5000100001"), "dashboard");
                Res ok = b.follow(b.submit("/transfer", Map.of("from_no", "5000100001", "to_no", "5000100002", "amount", "1500")));
                check(ok.body().contains("Transfer complete") && ok.body().contains("23,500.00"), "transfer result");
                Res nf = b.follow(b.submit("/transfer", Map.of("from_no", "5000100001", "to_no", "0000000000", "amount", "5")));
                check(nf.body().contains("Account not found"), "unknown account");
                Res min = b.follow(b.submit("/transfer", Map.of("from_no", "5000100001", "to_no", "5000100002", "amount", "99999")));
                check(min.body().contains("minimum balance"), "minimum balance rule");
                Res junk = b.follow(b.submit("/transfer", Map.of("from_no", "5000100001", "to_no", "5000100002", "amount", "1e999")));
                check(junk.body().contains("Enter a valid amount"), "junk amount");
                Res st = b.get("/account/5000100001?from=2020-01-01&to=2099-01-01");
                check(st.status() == 200 && st.body().contains("Statement"), "statement");
            });

            test("customer cannot open staff pages or other people's accounts", () -> {
                Browser b = new Browser(base);
                b.login("alice", "alice123");
                for (String p : new String[]{"/accounts/open", "/customers", "/users", "/audit", "/loans/review"}) {
                    eq(403, b.get(p).status(), p);
                }
                Browser t = new Browser(base);
                t.login("teller1", "teller123");
                Res open = t.follow(t.submit("/accounts/open", Map.of("type", "savings", "opening", "2000", "name", "Bob Rao",
                        "address", "5 Lane", "phone", "9000000001", "id_proof", "PAN ABCDE")));
                Matcher login = Pattern.compile("User ID: (cust\\d+)\\s+Temporary password: (\\w+)").matcher(open.body());
                check(login.find(), "temp login shown once");
                Browser bob = new Browser(base);
                eq(302, bob.login(login.group(1), login.group(2)).status(), "bob logs in");
                eq(403, bob.get("/account/5000100001").status(), "bob must not see alice's account");
            });

            test("teller: opens accounts, deposits, cannot do manager things, profile edit", () -> {
                Browser t = new Browser(base);
                t.login("teller1", "teller123");
                for (String p : new String[]{"/", "/accounts/open", "/customers", "/customers/1"}) eq(200, t.get(p).status(), p);
                eq(403, t.get("/users").status(), "teller /users");
                eq(403, t.submit("/interest", Map.of()).status(), "teller interest");
                Res dep = t.follow(t.submit("/account/5000100002/deposit", Map.of("amount", "50")));
                check(dep.body().contains("Deposit recorded"), "deposit message");
                Res close = t.follow(t.submit("/account/5000100002/close", Map.of()));
                check(close.body().contains("balance must be zero"), "close with balance");
                Res edit = t.follow(t.submit("/customers/1", Map.of("name", "Alice V", "address", "New addr", "phone", "9876543210", "id_proof", "Aadhaar")));
                check(edit.body().contains("Profile updated"), "profile saved");
                Res bad = t.submit("/customers/1", Map.of("name", "", "address", "x", "phone", "12", "id_proof", "x"));
                check(bad.body().contains("Name is required"), "validation message re-shown");
            });

            test("HTML in user input is escaped", () -> {
                Browser t = new Browser(base);
                t.login("teller1", "teller123");
                t.submit("/accounts/open", Map.of("type", "current", "opening", "10", "name", "<script>alert(1)</script>",
                        "address", "a", "phone", "9000000009", "id_proof", "x"));
                String list = t.get("/customers").body();
                check(list.contains("&lt;script&gt;alert(1)&lt;/script&gt;") && !list.contains("<script>alert(1)"), "escaped");
            });

            test("manager: loan desk, approval, repayment, staff admin, audit, interest", () -> {
                Browser c = new Browser(base);
                c.login("alice", "alice123");
                Res applied = c.follow(c.submit("/loans", Map.of("account_no", "5000100001", "amount", "10000", "tenure", "12", "purpose", "Laptop")));
                check(applied.body().contains("submitted"), "application");
                Browser m = new Browser(base);
                m.login("admin", "admin123");
                check(m.get("/loans/review").body().contains("Laptop"), "loan desk lists it");
                Res approved = m.follow(m.submit("/loans/1/decide", Map.of("decision", "approve")));
                check(approved.body().contains("approved and disbursed"), "approval");
                Res repaid = c.follow(c.submit("/loans/1/repay", Map.of("account_no", "5000100001", "amount", "500")));
                check(repaid.body().contains("still outstanding"), "repayment");
                Res created = m.follow(m.submit("/users", Map.of("user_id", "teller2", "full_name", "New Teller", "role", "teller", "password", "secret12")));
                check(created.body().contains("teller2"), "employee listed");
                check(m.get("/audit").body().contains("APPROVE_LOAN"), "audit shows approval");
                check(m.follow(m.submit("/interest", Map.of())).body().contains("Monthly interest credited"), "interest");
            });

            test("lockout over HTTP: three failures lock, manager unlocks", () -> {
                Browser b = new Browser(base);
                for (int i = 0; i < 3; i++) b.submit("/login", Map.of("user_id", "teller2", "password", "bad"));
                Res locked = b.submit("/login", Map.of("user_id", "teller2", "password", "secret12"));
                check(locked.body().contains("locked"), "locked message");
                Browser m = new Browser(base);
                m.login("admin", "admin123");
                Matcher id = Pattern.compile("/users/(\\d+)/unlock").matcher(m.get("/users").body());
                check(id.find(), "unlock button");
                m.submit("/users/" + id.group(1) + "/unlock", Map.of("back", "/users"));
                eq(302, new Browser(base).login("teller2", "secret12").status(), "login after unlock");
            });

            test("logout ends the session", () -> {
                Browser b = new Browser(base);
                b.login("alice", "alice123");
                eq(302, b.submit("/logout", Map.of()).status(), "logout");
                eq(302, b.get("/").status(), "home needs login again");
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }
    }
}
