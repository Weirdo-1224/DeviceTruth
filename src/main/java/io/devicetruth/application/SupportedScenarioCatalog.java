package io.devicetruth.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** S1 演示目录：只从打包资源 datasets/scenarios.json 派生，不手工维护第二份资产表。 */
public final class SupportedScenarioCatalog {
    /** 一个已登记模拟资产的领域、症状码与固定业务时间。 */
    public record SupportedDiagnosis(String assetId, String domain, String symptomCode, Instant diagnosisAsOf) {
        public SupportedDiagnosis {
            if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId is required");
            if (domain == null || domain.isBlank()) throw new IllegalArgumentException("domain is required");
            if (symptomCode == null || symptomCode.isBlank()) throw new IllegalArgumentException("symptomCode is required");
            if (diagnosisAsOf == null) throw new IllegalArgumentException("diagnosisAsOf is required");
        }
    }

    private final Map<String, SupportedDiagnosis> byAsset = new LinkedHashMap<>();

    public SupportedScenarioCatalog(List<SupportedDiagnosis> entries) {
        for (var entry : entries) {
            if (byAsset.putIfAbsent(entry.assetId(), entry) != null)
                throw new IllegalArgumentException("Duplicate asset in catalog: " + entry.assetId());
        }
    }

    public static SupportedScenarioCatalog fromClasspath() throws IOException {
        return fromNode(JsonRepositories.readResource("datasets/scenarios.json", JsonNode.class));
    }

    public static SupportedScenarioCatalog fromNode(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) throw new IOException("Invalid scenario catalog");
        var entries = new ArrayList<SupportedDiagnosis>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = root.get(name);
            DiagnosisRequest request = Json.MAPPER.treeToValue(value, DiagnosisRequest.class);
            entries.add(new SupportedDiagnosis(request.assetId(), request.domain(), request.symptomCode(), request.asOf()));
        }
        if (entries.isEmpty()) throw new IOException("Empty scenario catalog");
        return new SupportedScenarioCatalog(entries);
    }

    public Optional<SupportedDiagnosis> find(String assetId) {
        return assetId == null ? Optional.empty() : Optional.ofNullable(byAsset.get(assetId));
    }

    public boolean supports(String assetId) { return find(assetId).isPresent(); }

    public List<SupportedDiagnosis> all() { return List.copyOf(byAsset.values()); }
}