package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Models.*;
import java.util.List;

/** 数据仓库接口契约：能力网关背后只依赖这三个只读接口。 */
public final class Repositories {
    private Repositories() {}
    /** 资产档案仓库：出厂配置（profile）与更换事件历史（history）。 */
    public interface AssetRepository {
        List<EvidenceRecord> profile(String assetId);
        List<EvidenceRecord> history(String assetId, String componentId);
    }
    /** 证据检索仓库：按条件搜索元数据（search）与按 ID 取原始记录（get）。 */
    public interface EvidenceRepository {
        List<EvidenceMetadata> search(String assetId, String componentId, String sourceType);
        EvidenceRecord get(String assetId, String evidenceId);
    }
    /** 规则仓库：按领域+症状码查询候选规则。 */
    public interface RuleRepository { List<Rule> query(String domain, String symptomCode); }

    /** 查询失败异常：携带稳定的错误码（如 ASSET_NOT_FOUND），供上层映射为 ToolError。 */
    public static final class QueryException extends RuntimeException {
        private final String code;
        public QueryException(String code, String message) { super(message); this.code=code; }
        public String code() { return code; }
    }
}
