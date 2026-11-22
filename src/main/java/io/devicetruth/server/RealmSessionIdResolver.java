package io.devicetruth.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

public final class RealmSessionIdResolver implements HttpSessionIdResolver {
    private final CookieHttpSessionIdResolver user;
    private final CookieHttpSessionIdResolver admin;

    public RealmSessionIdResolver(boolean secure) {
        user = resolver("DTSESSION", "/", secure);
        admin = resolver("DTADMINSESSION", "/api/admin", secure);
    }

    private static CookieHttpSessionIdResolver resolver(String name, String path, boolean secure) {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName(name);
        cookie.setCookiePath(path);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(secure);
        cookie.setSameSite("Lax");
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(cookie);
        return resolver;
    }

    private CookieHttpSessionIdResolver resolver(HttpServletRequest request) {
        return LoginRealm.forRequest(request) == LoginRealm.ADMIN ? admin : user;
    }

    @Override public List<String> resolveSessionIds(HttpServletRequest request) {
        return resolver(request).resolveSessionIds(request);
    }
    @Override public void setSessionId(HttpServletRequest request, HttpServletResponse response, String id) {
        resolver(request).setSessionId(request, response, id);
    }
    @Override public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        resolver(request).expireSession(request, response);
    }
}
