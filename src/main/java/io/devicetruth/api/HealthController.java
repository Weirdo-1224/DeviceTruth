package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康探针：product 容器 healthcheck 与运维轮询依赖，两个 profile 都必须可用。 */
@RestController
@RequestMapping("/api")
public class HealthController {
    @GetMapping("/health")
    public HealthResponse health() { return new HealthResponse("UP", ApiDtos.SIMULATED); }
}
