package io.devicetruth.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Cookie 只选择会话；服务端标记才证明这份认证属于哪一端。 */
public enum LoginRealm {
    USER, ADMIN;

    public static final String SESSION_ATTRIBUTE = "DEVICETRUTH_LOGIN_REALM";

    public static LoginRealm forRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/") ? ADMIN : USER;
    }

    public boolean accepts(HttpSession session) {
        if (session == null) return false;
        Object actual = session.getAttribute(SESSION_ATTRIBUTE);
        // 已部署业务会话在引入域标记之前没有该属性，不能用它恢复管理身份。
        return name().equals(actual) || (this == USER && actual == null);
    }
}
