package com.passbook.web;

import com.passbook.model.Permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps "METHOD /path/{var}" to a handler plus the permission needed to call it. */
public final class Router {
    public static final class Route {
        final String method;
        final String[] segments;
        final boolean isPublic;
        final Permission permission;  // null = any logged-in user
        final Handler handler;

        Route(String method, String pattern, boolean isPublic, Permission permission, Handler handler) {
            this.method = method;
            this.segments = split(pattern);
            this.isPublic = isPublic;
            this.permission = permission;
            this.handler = handler;
        }

        Map<String, String> match(String[] path) {
            if (path.length != segments.length) return null;
            Map<String, String> vars = new HashMap<>();
            for (int i = 0; i < path.length; i++) {
                String s = segments[i];
                if (s.startsWith("{") && s.endsWith("}")) vars.put(s.substring(1, s.length() - 1), path[i]);
                else if (!s.equals(path[i])) return null;
            }
            return vars;
        }
    }

    public record Match(Route route, Map<String, String> params, boolean pathKnownForOtherMethod) {
    }

    private final List<Route> routes = new ArrayList<>();

    private static String[] split(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.isEmpty() ? new String[0] : p.split("/");
    }

    public void publicRoute(String method, String pattern, Handler h) {
        routes.add(new Route(method, pattern, true, null, h));
    }

    /** Any logged-in user. */
    public void route(String method, String pattern, Handler h) {
        routes.add(new Route(method, pattern, false, null, h));
    }

    /** Only users holding the permission. */
    public void route(String method, String pattern, Permission needed, Handler h) {
        routes.add(new Route(method, pattern, false, needed, h));
    }

    public Match find(String method, String path) {
        String[] segs = split(path);
        boolean other = false;
        for (Route r : routes) {
            Map<String, String> vars = r.match(segs);
            if (vars == null) continue;
            if (r.method.equals(method)) return new Match(r, vars, false);
            other = true;
        }
        return new Match(null, Map.of(), other);
    }
}
