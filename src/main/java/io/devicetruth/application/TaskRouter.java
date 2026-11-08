package io.devicetruth.application;

/** 轻量分类器：只决定 route，不执行查询/诊断，也不授予任何工具权限。 */
public interface TaskRouter {
    RouteDecision decide(RouteRequest request);

    /** 路由输入：用户消息、会话当前资产、请求体显式资产（三者都由程序复核）。 */
    record RouteRequest(String content, String currentAssetId, String assetIdHint) {
        public RouteRequest {
            if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        }
    }
}