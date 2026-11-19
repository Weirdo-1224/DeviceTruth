package io.devicetruth.api;

import io.devicetruth.application.agent.AssetFactService;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.Repositories.QueryException;

import io.devicetruth.core.contract.AssetFacts.ConfigurationOutcome;
import io.devicetruth.core.contract.AssetFacts.MaintenanceOutcome;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 设备事实只读 API（03-01）：身份取服务器 Bean，请求体不能提供 owner/project。
 * 跨归属读取统一返回 404：不区分“不存在”和“无权访问”，避免暴露他人资产是否存在。
 * 结果只描述记录、来源、有效期与核实状态，不返回诊断结论。
 */
@RestController
@Profile("product")
public class AssetController {
    private final AssetFactService service;
    private final io.devicetruth.server.HttpAccess access;
    private ServerIdentity identity() { return access.current().storageIdentity(); }

    public AssetController(AssetFactService service, io.devicetruth.server.HttpAccess access) {
        this.service = service;
        this.access = access;
    }

    /** 已登记资产清单（本人/项目范围）。 */
    @GetMapping("/api/assets")
    List<String> assets() { return service.assets(identity().ownerId(), identity().projectId()); }

    /**
     * 资产身份、组件清单，以及指定时点的配置/维修事实组合视图。
     * 未登记或跨归属一律 404，不透露资产是否存在于其他身份下。
     */
    @GetMapping("/api/assets/{assetId}")
    ResponseEntity<?> asset(@PathVariable String assetId, @RequestParam(required = false) String asOf) {
        Instant at = parseInstant(asOf);
        if (!service.exists(identity().ownerId(), identity().projectId(), assetId)) return notFound();
        return ResponseEntity.ok(service.composite(identity().ownerId(), identity().projectId(), assetId, at));
    }

    @GetMapping("/api/assets/{assetId}/configuration")
    ResponseEntity<?> configuration(@PathVariable String assetId, @RequestParam(required = false) String componentId,
                                    @RequestParam(required = false) String attribute,
                                    @RequestParam(required = false) String asOf) {
        Instant at = parseInstant(asOf);
        ConfigurationOutcome outcome = service.configuration(identity().ownerId(), identity().projectId(), assetId,
                componentId, attribute, at);
        return outcome.success() ? ResponseEntity.ok(outcome) : failure(outcome.errorCode(), outcome.message());
    }

    @GetMapping("/api/assets/{assetId}/maintenance")
    ResponseEntity<?> maintenance(@PathVariable String assetId, @RequestParam(required = false) String componentId,
                                  @RequestParam(required = false) String asOf) {
        Instant at = parseInstant(asOf);
        MaintenanceOutcome outcome = service.maintenance(identity().ownerId(), identity().projectId(), assetId,
                componentId, at);
        return outcome.success() ? ResponseEntity.ok(outcome) : failure(outcome.errorCode(), outcome.message());
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("asOf must be an ISO-8601 instant");
        }
    }

    /** 资产不存在、越权与非法资产 ID 都统一 404，避免用状态码区分“别人的资产存在与否”。 */
    private static ResponseEntity<?> failure(String code, String message) {
        int status = switch (code == null ? "" : code) {
            case "ASSET_NOT_FOUND", "PERMISSION_DENIED" -> 404;
            case "INVALID_ARGUMENTS" -> 400;
            default -> 500;
        };
        return ResponseEntity.status(status)
                .body(Map.of("code", code == null ? "NOT_FOUND_OR_DENIED" : code,
                        "message", message == null ? "请求无法处理。" : message, "retryable", false,
                        "traceId", UUID.randomUUID().toString()));
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND_OR_DENIED",
                "message", "资产不存在或无权访问", "retryable", false, "traceId", UUID.randomUUID().toString()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<?> accessFailure(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("code", e.getReason()==null?"NOT_FOUND_OR_DENIED":e.getReason()));
    }

    @ExceptionHandler(QueryException.class)
    ResponseEntity<?> queryFailure(QueryException failure) { return failure(failure.code(), failure.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalidArgument(IllegalArgumentException failure) {
        return failure("INVALID_ARGUMENTS", "请求参数不合法");
    }
}