package io.devicetruth.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.capability.Repositories.AssetComponent;
import io.devicetruth.core.capability.Repositories.AssetConfigurationRecord;
import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetMaintenanceRecord;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.Repositories.AssetSnapshot;
import io.devicetruth.core.contract.AssetFacts.VerificationStatus;
import io.devicetruth.core.contract.Checks;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 从打包的只读模拟夹具派生结构化资产快照（03-01）。
 * 不修改原 fixture、不改历史证据：只把已有记录映射成 asset/component/configuration/maintenance 行，
 * 来源与 SIMULATED 标记原样保留，便于授权后幂等导入 PostgreSQL。
 * 采用“证据即配置/维修记录”的边界：FACTORY 视为资料记载的配置，REPLACEMENT 视为维修，
 * 仅带 observedAt 的 FIELD_CONFIRMATION 才是现场核实；用户口述不在此处被升级。
 */
public final class AssetSnapshotImporter {
    private AssetSnapshotImporter() {}

    public static AssetSnapshot fromFixture(String ownerId, String projectId, String assetId) throws IOException {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.id(assetId, "assetId");
        String resource = "datasets/v1/factory/" + assetId.toLowerCase(Locale.ROOT) + ".json";
        if (AssetSnapshotImporter.class.getClassLoader().getResource(resource) == null)
            resource = "datasets/fixtures/" + fixtureFolder(assetId) + "/asset.json";
        JsonRepositories.Dataset dataset = JsonRepositories.readResource(resource, JsonRepositories.Dataset.class);
        if (!assetId.equals(dataset.assetId()))
            throw new IllegalArgumentException("Fixture asset mismatch: " + dataset.assetId());
        return fromEvidence(ownerId, projectId, dataset.assetId(), dataset.records());
    }

    /** 把一组证据记录映射为快照；只读，不改变输入。 */
    public static AssetSnapshot fromEvidence(String ownerId, String projectId, String assetId,
                                             List<EvidenceRecord> records) {
        AssetIdentity identity = new AssetIdentity(assetId, ownerId, projectId);
        List<AssetConfigurationRecord> configurations = new ArrayList<>();
        List<AssetMaintenanceRecord> maintenance = new ArrayList<>();
        Set<String> components = new LinkedHashSet<>();
        for (EvidenceRecord record : records) {
            if (!assetId.equals(record.assetId()))
                throw new IllegalArgumentException("Evidence belongs to another asset");
            String component = record.payload().get("subject");
            components.add(component);
            String attribute = record.payload().get("predicate");
            String value = record.payload().get("value");
            configurations.add(new AssetConfigurationRecord(stableId("config", record.evidenceId()), assetId,
                    component, attribute, value, record.sourceType(), record.evidenceId(), record.sourceRef(),
                    "SIMULATED", verification(record).name(), record.authority(), record.validFrom(),
                    record.validTo(), record.recordedAt(), record.observedAt()));
            if ("REPLACEMENT".equals(record.sourceType()))
                maintenance.add(new AssetMaintenanceRecord(stableId("maint", record.evidenceId()), assetId,
                        component, "REPLACEMENT", null, value, record.sourceType(), record.evidenceId(),
                        record.sourceRef(), "SIMULATED", VerificationStatus.DOCUMENTED.name(), record.authority(),
                        record.validFrom(), record.validTo(), record.recordedAt(), record.observedAt()));
        }
        List<AssetComponent> componentRows = components.stream()
                .map(id -> new AssetComponent(assetId, id, id)).toList();
        return new AssetSnapshot(identity, componentRows, configurations, maintenance);
    }

    /**
     * 核实状态只由来源类型决定：现场确认必须有观察时间；资料与用户陈述一律 DOCUMENTED。
     * 这是“资料写得最新 ≠ 现场已确认”的结构性保证。
     */
    static VerificationStatus verification(EvidenceRecord record) {
        return "FIELD_CONFIRMATION".equals(record.sourceType()) && record.observedAt() != null
                ? VerificationStatus.FIELD_VERIFIED : VerificationStatus.DOCUMENTED;
    }

    /** 与数据集资产对应的夹具目录（A→a … D→d）。 */
    private static String fixtureFolder(String assetId) {
        String suffix = assetId.startsWith("CAR-") ? assetId.substring(4) : assetId;
        if (suffix.length() != 1) throw new IllegalArgumentException("No packaged fixture for " + assetId);
        return suffix.toLowerCase(java.util.Locale.ROOT);
    }

    private static String stableId(String prefix, String evidenceId) {
        return java.util.UUID.nameUUIDFromBytes((prefix + ":" + evidenceId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /** 解析导入用的 JSON 快照文件（运维显式导入）；未知字段一律报错，不做宽松转换。 */
    public static AssetSnapshot read(JsonNode node) {
        Checks.required(node, "snapshot");
        return Json.MAPPER.convertValue(node, AssetSnapshot.class);
    }

    /** 便捷入口：授权后幂等导入。 */
    public static void importFixture(AssetRegistryRepository registry, String ownerId, String projectId,
                                     String assetId) throws IOException {
        registry.importSnapshot(fromFixture(ownerId, projectId, assetId));
    }
}
