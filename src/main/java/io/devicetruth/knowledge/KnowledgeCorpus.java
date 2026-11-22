package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.contract.Checks;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Frozen-corpus adapter. Original YAML/body/source records are losslessly retained. */
public final class KnowledgeCorpus {
    public static final String ACCESS_NATURE = "AUTHORIZED";
    public static final String AUTHORIZATION_BASIS = "USER_AUTHORIZED_LOCAL_TECHNICAL_RESEARCH_IMPORT";
    public record Document(String knowledgeId, String title, String domain, String category, String manufacturer,
                           List<String> deviceModels, List<String> components, List<String> applicableVersions,
                           List<String> sourceIds, List<Map<String,Object>> sourceLocations,
                           String trustLevel, String verificationStatus, String sourceNature,
                           List<String> relatedSkills, List<String> keywords, String originalChecksum,
                           String originalText, String body, int bodyStart, Map<String,Object> originalMetadata,
                           Map<String,Map<String,Object>> sources, List<String> preservedExtensionFields) {
        public Map<String,Object> metadata(String kbRelease) {
            var m = new LinkedHashMap<String,Object>();
            m.put("knowledge_id",knowledgeId);m.put("title",title);m.put("domain",domain);m.put("category",category);
            m.put("manufacturer",manufacturer);m.put("device_models",deviceModels);m.put("components",components);
            m.put("applicable_versions",applicableVersions);
            m.put("applicability_scope",Map.of("market",originalMetadata.getOrDefault("market","unknown"),
                    "scope_level",originalMetadata.getOrDefault("scope_level","unknown"),
                    "version_semantics","UNCLASSIFIED_SOURCE_STATEMENTS_NOT_INFERRED_DEVICE_FW", "unknown_is_wildcard",false));
            m.put("source_ids",sourceIds);m.put("source_locations",sourceLocations);m.put("trust_level",trustLevel);
            m.put("verification_status",verificationStatus);m.put("original_data_nature",sourceNature);
            m.put("source_type",originalMetadata.get("source_type"));m.put("data_nature",ACCESS_NATURE);
            m.put("authorization_basis",AUTHORIZATION_BASIS);m.put("field_fact",false);
            m.put("related_skills",relatedSkills);m.put("keywords",keywords);m.put("original_checksum",originalChecksum);
            m.put("kb_release",kbRelease);m.put("original_yaml",originalMetadata);
            m.put("preserved_extension_fields",preservedExtensionFields);
            m.put("manufacturer_document_versions",sourceIds.stream().map(s -> Map.of("source_id",s,
                    "document_version",sources.get(s).getOrDefault("document_version","unknown"))).toList());
            return m;
        }
    }
    private KnowledgeCorpus() {}
    private static final Set<String> MAPPED = Set.of("knowledge_id","title","domain","category","manufacturer",
            "device_model","component","applicable_versions","source_ids","source_locations","trust_level",
            "verification_status","data_nature","related_skills","keywords","source_type","market","scope_level","field_fact");

    public static List<Document> load(Path knowledgeRoot) {
        try {
            Path root=knowledgeRoot.toRealPath(), project=root.getParent().getParent();
            var manifest=Json.MAPPER.readTree(root.resolve("reports/knowledge_v1_manifest.json").toFile());
            if(!hash(Files.readAllBytes(root.resolve("sources/source_registry.jsonl"))).equals(manifest.path("source_registry_sha256").asText()))throw invalid("SOURCE_REGISTRY_HASH_MISMATCH");
            var registry=new LinkedHashMap<String,Map<String,Object>>();
            for(String line:Files.readAllLines(root.resolve("sources/source_registry.jsonl"))) {
                if(line.isBlank())continue;
                Map<String,Object> source=Json.MAPPER.readValue(line,new com.fasterxml.jackson.core.type.TypeReference<>(){});
                String id=(String)source.get("source_id");
                if(registry.put(id,source)!=null)throw invalid("DUPLICATE_SOURCE_ID");
            }
            var result=new ArrayList<Document>();var ids=new java.util.HashSet<String>();
            for(var row:manifest.path("documents")) {
                Path file=project.resolve(row.path("path").asText()).toRealPath();
                if(!file.startsWith(root.resolve("curated")))throw invalid("CORPUS_PATH_INVALID");
                var doc=parse(Files.readAllBytes(file),row.path("sha256").asText(),registry);
                if(!doc.knowledgeId().equals(row.path("knowledge_id").asText()) || !ids.add(doc.knowledgeId()))throw invalid("CORPUS_ID_MISMATCH");
                result.add(doc);
            }
            if(result.size()!=manifest.path("document_count").asInt())throw invalid("CORPUS_COUNT_MISMATCH");
            var checked=new java.util.HashSet<String>();
            for(var d:result)for(var source:d.sources().entrySet())if(checked.add(source.getKey())) {
                var record=source.getValue();Path file=project.resolve((String)record.get("local_path")).toRealPath();
                if(!file.startsWith(project) || !hash(Files.readAllBytes(file)).equals(record.get("sha256")))throw invalid("SOURCE_FILE_HASH_MISMATCH");
            }
            return List.copyOf(result);
        } catch(KnowledgeException e){throw e;} catch(Exception e){throw invalid("CORPUS_READ_FAILED");}
    }

    @SuppressWarnings("unchecked")
    public static Document parse(byte[] bytes,String expectedChecksum,Map<String,Map<String,Object>> registry) {
        if(!hash(bytes).equals(expectedChecksum))throw invalid("KNOWLEDGE_HASH_MISMATCH");
        try {
            String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if(bytes.length>1_000_000 || !raw.startsWith("---\n"))throw invalid("INVALID_KNOWLEDGE_FRONTMATTER");
            var closing=Pattern.compile("(?m)^---[ \\t]*\\r?$").matcher(raw);closing.find();
            if(!closing.find())throw invalid("INVALID_KNOWLEDGE_FRONTMATTER");
            int begin=closing.end();while(begin<raw.length() && Character.isWhitespace(raw.charAt(begin)))begin++;
            int end=raw.length();while(end>begin && Character.isWhitespace(raw.charAt(end-1)))end--;
            String body=raw.substring(begin,end);if(body.isBlank())throw invalid("EMPTY_KNOWLEDGE_BODY");
            var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setCodePointLimit(1_000_000);
            Object loaded=new Yaml(new SafeConstructor(options)).load(raw.substring(4,closing.start()));
            if(!(loaded instanceof Map<?,?>))throw invalid("INVALID_METADATA_MAP");
            Map<String,Object> m=(Map<String,Object>)loaded;
            String id=Checks.id(text(m,"knowledge_id"),"knowledge_id"),nature=text(m,"data_nature");
            if(!Set.of("REAL","PROJECT_RULE").contains(nature) || Boolean.TRUE.equals(m.get("field_fact")))throw invalid("INVALID_KNOWLEDGE_NATURE");
            if(nature.equals("PROJECT_RULE") && (!text(m,"manufacturer").equals("PROJECT") || !text(m,"source_type").equals("PROJECT_RULE")))throw invalid("PROJECT_RULE_IDENTITY_INVALID");
            if(!text(m,"verification_status").equals("VERIFIED"))throw invalid("KNOWLEDGE_NOT_VERIFIED");
            List<String> sourceIds=list(m,"source_ids");var sources=new LinkedHashMap<String,Map<String,Object>>();
            for(String source:sourceIds) {
                if(!registry.containsKey(source))throw invalid("SOURCE_NOT_REGISTERED");
                sources.put(source,registry.get(source));
            }
            if(!(m.get("source_locations") instanceof List<?> locations) || locations.isEmpty())throw invalid("SOURCE_LOCATION_MISSING");
            for(Object item:locations) {
                if(!(item instanceof Map<?,?> loc) || !sources.containsKey(loc.get("source_id")) ||
                        (loc.get("pdf_page")==null && loc.get("section")==null && loc.get("anchor")==null))throw invalid("SOURCE_LOCATION_INVALID");
            }
            return new Document(id,text(m,"title"),Checks.id(text(m,"domain"),"domain"),text(m,"category"),text(m,"manufacturer"),
                    list(m,"device_model"),list(m,"component"),list(m,"applicable_versions"),sourceIds,(List<Map<String,Object>>)m.get("source_locations"),
                    text(m,"trust_level"),text(m,"verification_status"),nature,list(m,"related_skills"),list(m,"keywords"),
                    expectedChecksum,raw,body,begin,m,sources,m.keySet().stream().filter(k->!MAPPED.contains(k)).sorted().toList());
        } catch(KnowledgeException e){throw e;} catch(Exception e){throw invalid("KNOWLEDGE_METADATA_INVALID");}
    }
    private static String text(Map<String,Object> m,String key) {
        if(!(m.get(key) instanceof String s) || s.isBlank())throw invalid("METADATA_REQUIRED_"+key.toUpperCase());return s;
    }
    private static List<String> list(Map<String,Object> m,String key) {
        if(!(m.get(key) instanceof List<?> list) || list.isEmpty() || list.stream().anyMatch(x->!(x instanceof String s)||s.isBlank()))throw invalid("METADATA_REQUIRED_"+key.toUpperCase());
        return list.stream().map(String.class::cast).toList();
    }
    public static String hash(String text){return hash(text.getBytes(StandardCharsets.UTF_8));}
    public static String hash(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new IllegalStateException("SHA256_UNAVAILABLE");}
    }
    public static UUID stableId(String identity){return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));}
    public static String modelKey(String model){return "model-"+hash(model).substring(0,24);}
    static KnowledgeException invalid(String code){return new KnowledgeException(code,400);}
}
