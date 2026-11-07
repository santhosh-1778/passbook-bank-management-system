package com.passbook.web;

import com.passbook.exception.AccessDeniedException;
import com.passbook.exception.AccountNotFoundException;
import com.passbook.exception.BankException;
import com.passbook.model.User;
import com.passbook.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The HTTP front door: session + login check, CSRF check, permission check, routing, and error pages.
 * Controllers only ever see requests that already passed all of these.
 */
public final class BankServer {
    private static final String COOKIE = "SID";
    private static final Map<String, String> TYPES = Map.of(
            "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8", "svg", "image/svg+xml",
            "png", "image/png", "ico", "image/x-icon");

    private final Router router;
    private final SessionStore sessions;
    private final AuthService auth;
    private final Path staticDir;
    private HttpServer server;

    public BankServer(Router router, SessionStore sessions, AuthService auth, Path staticDir) {
        this.router = router;
        this.sessions = sessions;
        this.auth = auth;
        this.staticDir = staticDir.toAbsolutePath().normalize();
    }

    /** Starts listening; pass port 0 to let the system pick one. Returns the real port. */
    public int start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::dispatch);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void dispatch(HttpExchange ex) throws IOException {
        Response res;
        Request req = null;
        try {
            req = Request.parse(ex);
            res = handle(req);
        } catch (Throwable t) {
            t.printStackTrace();
            res = Response.html(500, "<h1>Something went wrong</h1>");
        }
        write(ex, req, res);
    }

    private Response handle(Request req) {
        String method = req.method();
        if (!method.equals("GET") && !method.equals("POST")) return Response.empty(405);

        if (req.path().startsWith("/static/")) return staticFile(req.path().substring("/static/".length()));
        if (req.path().equals("/favicon.ico")) return Response.empty(204);

        // ---- session and current user
        Session session = sessions.find(req.cookie(COOKIE));
        if (session == null) session = sessions.create();
        req.setSession(session);
        if (session.userId() != null) {
            User u = auth.find(session.userId()).filter(x -> x.isActive() && !x.isLocked()).orElse(null);
            if (u == null) {  // account disabled or locked since login
                sessions.destroy(session);
                session = sessions.create();
                req.setSession(session);
            }
            req.setUser(u);
        }

        try {
            // ---- CSRF: every POST must carry the session's token
            if (method.equals("POST") && !tokenMatches(req.form("_csrf"), req.session().csrf())) {
                return errorPage(req, 400, "That form has expired", "Go back, refresh the page and try again.");
            }
            Router.Match m = router.find(method, req.path());
            if (m.route() == null) {
                return m.pathKnownForOtherMethod() ? Response.empty(405)
                        : errorPage(req, 404, "No such page", "That address does not exist.");
            }
            if (!m.route().isPublic) {
                if (req.user() == null) {
                    return Response.redirect("/login?next=" + java.net.URLEncoder.encode(req.path(), StandardCharsets.UTF_8));
                }
                if (m.route().permission != null && !req.user().can(m.route().permission)) {
                    return errorPage(req, 403, "Not for your role", "Your role does not allow this page.");
                }
            }
            req.setParams(m.params());
            Response r = m.route().handler.handle(req);
            return r != null ? r : errorPage(req, 404, "No such page", "That address does not exist.");
        } catch (AccessDeniedException e) {
            return errorPage(req, 403, "Not for your role", e.getMessage());
        } catch (AccountNotFoundException e) {
            return errorPage(req, 404, "No such account", e.getMessage());
        } catch (BankException e) {
            return errorPage(req, 400, "Request not accepted", e.getMessage());
        }
    }

    private Response errorPage(Request req, int code, String title, String message) {
        ViewContext c = new ViewContext(req.user(), req.session().csrf(), req.path(), List.of(), LocalDate.now());
        return Response.html(code, AuthViews.error(c, code, title, message));
    }

    private static boolean tokenMatches(String sent, String real) {
        return real != null && MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), real.getBytes(StandardCharsets.UTF_8));
    }

    private Response staticFile(String name) {
        try {
            Path file = staticDir.resolve(name).normalize();
            if (!file.startsWith(staticDir) || !Files.isRegularFile(file)) return Response.empty(404);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            return Response.bytes(TYPES.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(file));
        } catch (IOException | RuntimeException e) {
            return Response.empty(404);
        }
    }

    private void write(HttpExchange ex, Request req, Response res) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", res.contentType());
        h.set("X-Frame-Options", "DENY");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", req != null && req.path().startsWith("/static/") ? "max-age=300" : "no-store");
        if (res.location() != null) h.set("Location", res.location());
        if (req != null && req.session() != null && !req.session().id().equals(req.cookie(COOKIE))) {
            h.add("Set-Cookie", COOKIE + "=" + req.session().id() + "; Path=/; HttpOnly; SameSite=Lax");
        }
        byte[] body = res.body();
        if (body.length == 0) {
            ex.sendResponseHeaders(res.status(), -1);
        } else {
            ex.sendResponseHeaders(res.status(), body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
        ex.close();
    }
}
