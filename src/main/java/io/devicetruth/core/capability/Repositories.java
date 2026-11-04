package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Models.*;
import java.util.List;

public final class Repositories {
    private Repositories() {}
    public interface AssetRepository {
        List<EvidenceRecord> profile(String assetId);
        List<EvidenceRecord> history(String assetId, String componentId);
    }
    public interface EvidenceRepository {
        List<EvidenceMetadata> search(String assetId, String componentId, String sourceType);
        EvidenceRecord get(String assetId, String evidenceId);
    }
    public interface RuleRepository { List<Rule> query(String domain, String symptomCode); }

    public static final class QueryException extends RuntimeException {
        private final String code;
        public QueryException(String code, String message) { super(message); this.code=code; }
        public String code() { return code; }
    }
}
