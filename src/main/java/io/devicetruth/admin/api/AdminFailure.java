package io.devicetruth.admin.api;

import java.util.Map;

/** 只接受宿主稳定码，不能携带底层异常或用户输入。 */
public final class AdminFailure extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String, Object> details;
    public AdminFailure(int status, String code) { this(status, code, Map.of()); }
    public AdminFailure(int status, String code, Map<String, Object> details) {
        super(code); this.status = status; this.code = code; this.details = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));
    }
    public static AdminFailure input() { return new AdminFailure(400, "INVALID_ADMIN_INPUT"); }
    public static AdminFailure missing() { return new AdminFailure(404, "RESOURCE_NOT_FOUND"); }
}
