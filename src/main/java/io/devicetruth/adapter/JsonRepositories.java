package io.devicetruth.adapter;

import io.devicetruth.core.capability.Repositories.*;
import io.devicetruth.core.contract.Models.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基于 JSON 文件的仓库实现：同时实现资产/证据/规则三个仓库接口，数据全部打包在 classpath。 */
public final class JsonRepositories implements AssetRepository, EvidenceRepository, RuleRepository {
    /** 数据集：一个资产的若干证据记录。构造时强制 SIMULATED 数据性质、防御性拷贝并核对资产归属。 */
    public record Dataset(String dataNature, String assetId, List<EvidenceRecord> records) {
        public Dataset {
            if (!"SIMULATED".equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED data accepted");
            records=List.copyOf(records);
            if(records.stream().anyMatch(e->!e.assetId().equals(assetId))) throw new IllegalArgumentException("Dataset asset mismatch");
        }
    }
    /** 规则包：一组同版本规则，同样只允许 SIMULATED 来源。 */
    public record RulePack(String dataNature, String version, List<Rule> rules) {
        public RulePack {
            if (!"SIMULATED".equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED rules accepted");
            rules=List.copyOf(rules);
        }
    }
    private final Map<String,Dataset> assets = new LinkedHashMap<>();
    private final List<Rule> rules;
    /** 构造时做全量完整性检查：资产 ID 唯一、证据 ID 全局唯一、规则 ID 唯一。 */
    public JsonRepositories(List<Dataset> datasets, RulePack pack) {
        for (var dataset:datasets) {
            if(assets.putIfAbsent(dataset.assetId(),dataset)!=null) throw new IllegalArgumentException("Duplicate asset");
        }
        var ids=new java.util.HashSet<String>();
        for(var dataset:datasets) for(var evidence:dataset.records())
            if(!ids.add(evidence.evidenceId())) throw new IllegalArgumentException("Duplicate evidence ID");
        this.rules=pack.rules();
        if(rules.stream().map(Rule::ruleId).distinct().count()!=rules.size()) throw new IllegalArgumentException("Duplicate rule ID");
    }
    /** 从 classpath 加载全部内置数据：a/b/c/d 四个场景的资产档案 + 演示规则包。 */
    public static JsonRepositories fromClasspath() throws IOException {
        var datasets=new java.util.ArrayList<Dataset>();
        for(String name:List.of("a","b","c","d")) datasets.add(readResource("datasets/fixtures/"+name+"/asset.json",Dataset.class));
        return new JsonRepositories(datasets,readResource("skills/automotive-demo/rules.json",RulePack.class));
    }
    /** 按路径读取 classpath 资源；资源缺失视为打包缺陷，抛 IOException。 */
    public static <T> T readResource(String resource, Class<T> type) throws IOException {
        try(InputStream input=JsonRepositories.class.getClassLoader().getResourceAsStream(resource)) {
            if(input==null) throw new IOException("Missing packaged resource: "+resource);
            return Json.MAPPER.readValue(input,type);
        }
    }
    public static Dataset readDataset(Path path) throws IOException {
        try(var input=Files.newInputStream(path)) { return Json.MAPPER.readValue(input,Dataset.class); }
    }
    private Dataset asset(String assetId) {
        var dataset=assets.get(assetId);
        if(dataset==null) throw new QueryException("ASSET_NOT_FOUND","Asset not found in simulated repository");
        return dataset;
    }
    @Override public List<EvidenceRecord> profile(String assetId) {
        // 出厂档案 = 全部 FACTORY 来源的记录
        return asset(assetId).records().stream().filter(e->e.sourceType().equals("FACTORY")).toList();
    }
    @Override public List<EvidenceRecord> history(String assetId,String componentId) {
        // 更换历史 = 全部 REPLACEMENT 来源的记录，可按组件过滤
        return asset(assetId).records().stream().filter(e->e.sourceType().equals("REPLACEMENT"))
                .filter(e->componentId==null||componentId.equals(e.payload().get("subject"))).toList();
    }
    @Override public List<EvidenceMetadata> search(String assetId,String componentId,String sourceType) {
        return asset(assetId).records().stream()
                .filter(e->componentId==null||componentId.equals(e.payload().get("subject")))
                .filter(e->sourceType==null||sourceType.equals(e.sourceType())).map(EvidenceMetadata::from).toList();
    }
    @Override public EvidenceRecord get(String assetId,String evidenceId) {
        // 按 ID 精确取一条；不存在或不属于该资产时按"超出范围"处理
        return asset(assetId).records().stream().filter(e->e.evidenceId().equals(evidenceId)).findFirst()
                .orElseThrow(()->new QueryException("EVIDENCE_NOT_FOUND","Evidence absent or outside current asset scope"));
    }
    @Override public List<Rule> query(String domain,String symptomCode) {
        return rules.stream().filter(r->r.domain().equals(domain)&&r.symptomCode().equals(symptomCode)).toList();
    }
}
