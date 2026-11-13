package io.devicetruth.application.agent;

import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.contract.Checks;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备主数据夹具的幂等导入（03-01）。
 * 只在运维显式开启时执行；导入目标是服务器身份对应的 owner/project，客户端与模型不能指定。
 * 导入只插入缺失记录，不删除、不改写既有来源或历史证据；包装夹具保持只读，不被修改。
 */
public final class AssetFixtureSeeder {
    private static final Logger LOG = LoggerFactory.getLogger(AssetFixtureSeeder.class);

    private final AssetRegistryRepository registry;
    private final ServerIdentity identity;
    private final boolean enabled;
    private final String fixtures;

    public AssetFixtureSeeder(AssetRegistryRepository registry, ServerIdentity identity, boolean enabled,
                              String fixtures) {
        Checks.required(registry, "registry");
        Checks.required(identity, "identity");
        this.registry = registry;
        this.identity = identity;
        this.enabled = enabled;
        this.fixtures = fixtures == null ? "" : fixtures;
    }

    /** Bean 初始化入口：关闭时什么都不做，开启时逐个资产导入并记录数量。 */
    public void seed() {
        if (!enabled) return;
        String[] assets = Arrays.stream(fixtures.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (assets.length == 0) {
            LOG.warn("Asset fixture seeding is enabled but no fixtures are configured");
            return;
        }
        for (String assetId : assets) {
            try {
                AssetSnapshotImporter.importFixture(registry, identity.ownerId(), identity.projectId(), assetId);
                LOG.info("Asset fixture seeded: {}", assetId);
            } catch (IOException | RuntimeException failure) {
                // 单个夹具失败不能阻断服务启动，但必须留痕，不能静默变成“没有数据”。
                LOG.error("Asset fixture seeding failed: assetId={} type={}", assetId,
                        failure.getClass().getSimpleName());
            }
        }
    }
}