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

public final class JsonRepositories implements AssetRepository, EvidenceRepository, RuleRepository {
    public record Dataset(String dataNature, String assetId, List<EvidenceRecord> records) {
        public Dataset {
            if (!"SIMULATED".equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED data accepted");
            records=List.copyOf(records);
            if(records.stream().anyMatch(e->!e.assetId().equals(assetId))) throw new IllegalArgumentException("Dataset asset mismatch");
        }
    }
    public record RulePack(String dataNature, String version, List<Rule> rules) {
        public RulePack {
            if (!"SIMULATED".equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED rules accepted");
            rules=List.copyOf(rules);
        }
    }
    private final Map<String,Dataset> assets = new LinkedHashMap<>();
    private final List<Rule> rules;
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
    public static JsonRepositories fromClasspath() throws IOException {
        var datasets=new java.util.ArrayList<Dataset>();
        for(String name:List.of("a","b","c","d")) datasets.add(readResource("datasets/fixtures/"+name+"/asset.json",Dataset.class));
        return new JsonRepositories(datasets,readResource("skills/automotive-demo/rules.json",RulePack.class));
    }
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
        return asset(assetId).records().stream().filter(e->e.sourceType().equals("FACTORY")).toList();
    }
    @Override public List<EvidenceRecord> history(String assetId,String componentId) {
        return asset(assetId).records().stream().filter(e->e.sourceType().equals("REPLACEMENT"))
                .filter(e->componentId==null||componentId.equals(e.payload().get("subject"))).toList();
    }
    @Override public List<EvidenceMetadata> search(String assetId,String componentId,String sourceType) {
        return asset(assetId).records().stream()
                .filter(e->componentId==null||componentId.equals(e.payload().get("subject")))
                .filter(e->sourceType==null||sourceType.equals(e.sourceType())).map(EvidenceMetadata::from).toList();
    }
    @Override public EvidenceRecord get(String assetId,String evidenceId) {
        return asset(assetId).records().stream().filter(e->e.evidenceId().equals(evidenceId)).findFirst()
                .orElseThrow(()->new QueryException("EVIDENCE_NOT_FOUND","Evidence absent or outside current asset scope"));
    }
    @Override public List<Rule> query(String domain,String symptomCode) {
        return rules.stream().filter(r->r.domain().equals(domain)&&r.symptomCode().equals(symptomCode)).toList();
    }
}
