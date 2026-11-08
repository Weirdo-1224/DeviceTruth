package io.devicetruth.application;

/** Router 的程序复核结果：route 是唯一可执行信息，reason 是固定内部原因码，不拼接用户原文。 */
public record RouteDecision(Route route, String assetId, QueryKind queryKind, String symptomCode, String reason) {
    public RouteDecision {
        if (route == null) throw new IllegalArgumentException("route is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
    }

    public boolean isExecutable() { return route == Route.QUERY || route == Route.DIAGNOSE; }

    public static RouteDecision query(String assetId, QueryKind kind) {
        return new RouteDecision(Route.QUERY, assetId, kind, null, "QUERY_READY");
    }

    public static RouteDecision diagnose(String assetId, String symptomCode) {
        return new RouteDecision(Route.DIAGNOSE, assetId, null, symptomCode, "DIAGNOSE_READY");
    }

    public static RouteDecision clarify(String assetId, String reason) {
        return new RouteDecision(Route.CLARIFY, assetId, null, null, reason);
    }

    public static RouteDecision unsupported(String reason) {
        return new RouteDecision(Route.UNSUPPORTED, null, null, null, reason);
    }
}