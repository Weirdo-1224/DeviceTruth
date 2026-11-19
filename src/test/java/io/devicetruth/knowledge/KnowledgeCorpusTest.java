package io.devicetruth.knowledge;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeCorpusTest {
    static List<KnowledgeCorpus.Document> docs;
    @BeforeAll static void load(){docs=KnowledgeCorpus.load(Path.of("datasets/knowledge"));}
    @Test void parsesAllFrozenYamlAndPreservesHashesAndSources() {
        assertThat(docs).hasSize(343);
        for(var d:docs){assertThat(KnowledgeCorpus.hash(d.originalText())).isEqualTo(d.originalChecksum());
            assertThat(d.originalText().substring(d.bodyStart(),d.bodyStart()+d.body().length())).isEqualTo(d.body());
            assertThat(d.sources()).containsKeys(d.sourceIds().toArray(String[]::new));}
    }
    @Test void rejectsChangedOriginalHash(){var d=docs.getFirst();assertThatThrownBy(()->KnowledgeCorpus.parse(d.originalText().getBytes(java.nio.charset.StandardCharsets.UTF_8),"0".repeat(64),d.sources())).hasMessage("KNOWLEDGE_HASH_MISMATCH");}
    @Test void rejectsUnregisteredSource(){var d=docs.getFirst();assertThatThrownBy(()->KnowledgeCorpus.parse(d.originalText().getBytes(java.nio.charset.StandardCharsets.UTF_8),d.originalChecksum(),java.util.Map.of())).hasMessage("SOURCE_NOT_REGISTERED");}
    @Test void rejectsDuplicateYamlKeys(){var d=docs.getFirst();String raw=d.originalText().replaceFirst("title:","title: duplicate\ntitle:");assertThatThrownBy(()->KnowledgeCorpus.parse(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),KnowledgeCorpus.hash(raw),d.sources())).hasMessage("KNOWLEDGE_METADATA_INVALID");}
    @Test void mappingKeepsRuleNatureSeparateFromAuthorizedAccess(){var d=docs.stream().filter(x->x.sourceNature().equals("PROJECT_RULE")).findFirst().orElseThrow();var m=d.metadata("kb-v1");assertThat(m).containsEntry("data_nature","AUTHORIZED").containsEntry("original_data_nature","PROJECT_RULE").containsEntry("field_fact",false);assertThat(m.get("original_yaml")).isEqualTo(d.originalMetadata());}
    @Test void keepsEveryExtensionAndOriginalVersionStatement(){for(var d:docs){var m=d.metadata("kb-v1");assertThat(m).containsEntry("applicable_versions",d.applicableVersions()).containsEntry("preserved_extension_fields",d.preservedExtensionFields());}}
    @Test void tokenCounterIsDeclaredReproducibleProxy(){assertThat(ContextualKnowledgeChunker.tokens("hello world")).isEqualTo(2);assertThat(KnowledgeIndexProfile.TOKENIZER).contains("not-qwen-billing");}
    @Test void wholeParentAndChildrenReconstructExactOriginalBodyForAllTokenExperiments(){
        var chunker=new ContextualKnowledgeChunker();
        for(int size:List.of(256,512,1024,0))for(var d:docs){var children=chunker.chunk(d,new KnowledgeIndexProfile("E2",size,KnowledgeIndexProfile.Context.NONE),"test");
            assertThat(children.stream().map(ContextualKnowledgeChunker.Child::content).reduce("",String::concat)).isEqualTo(d.body());
            for(int i=0;i<children.size();i++){var c=children.get(i);assertThat(c.content()).isEqualTo(d.body().substring(c.bodyStart(),c.bodyEnd()));
                assertThat(c.nextChunkId()).isEqualTo(i+1<children.size()?children.get(i+1).chunkId():null);assertThat(c.previousChunkId()).isEqualTo(i>0?children.get(i-1).chunkId():null);}}
    }
    @Test void contextualTextDoesNotRewriteEvidenceOrIncludeManagementUrls(){var d=docs.getFirst();for(var context:KnowledgeIndexProfile.Context.values()){
        var c=new ContextualKnowledgeChunker().chunk(d,new KnowledgeIndexProfile("E3",512,context),"test").getFirst();assertThat(c.embeddingText()).endsWith(c.content());
        String prefix=ContextualKnowledgeChunker.prefix(d,c.sectionPath(),context);assertThat(prefix).doesNotContain("https://","source_ids:","verification_status:");
        if(context==KnowledgeIndexProfile.Context.NONE)assertThat(c.embeddingText()).isEqualTo(c.content());}}
    @Test void chunkIdsAreStableAndIsolatedByScopeAndProfile(){var d=docs.getFirst();var ch=new ContextualKnowledgeChunker();var p=new KnowledgeIndexProfile("E3",256,KnowledgeIndexProfile.Context.METADATA);var a=ch.chunk(d,p,"owner-a");assertThat(ch.chunk(d,p,"owner-a")).isEqualTo(a);assertThat(ch.chunk(d,p,"owner-b").getFirst().chunkId()).isNotEqualTo(a.getFirst().chunkId());assertThat(ch.chunk(d,new KnowledgeIndexProfile("E3",256,KnowledgeIndexProfile.Context.TITLE),"owner-a").getFirst().chunkId()).isNotEqualTo(a.getFirst().chunkId());}
    @Test void technicalIdentifiersTablesAndConditionsRemainAtomic(){
        String paragraph="型号6ES7214-1AG40-0XB0；故障A.810适用条件：24 V DC，版本V4.6；安全限制必须同时成立。";
        String body="# 参数\n\n"+paragraph.repeat(15)+"\n\n|型号|单位|\n|---|---|\n|48TFE014-501|Pa|";
        var d=bodyDocument(body);var children=new ContextualKnowledgeChunker().chunk(d,new KnowledgeIndexProfile("E2",256,KnowledgeIndexProfile.Context.NONE),"test");
        assertThat(children).anyMatch(ContextualKnowledgeChunker.Child::exceedsTarget);
        assertThat(children).anyMatch(c->c.content().contains(paragraph.repeat(15)));
        assertThat(children).anyMatch(c->c.content().contains("|型号|单位|\n|---|---|\n|48TFE014-501|Pa|"));
    }
    @Test void shortCompleteDocumentIsNotForcedIntoChildren(){var d=bodyDocument("# 正常\n\n短文完整保留。");assertThat(new ContextualKnowledgeChunker().chunk(d,new KnowledgeIndexProfile("E2",256,KnowledgeIndexProfile.Context.NONE),"test")).hasSize(1);}
    @Test void rejectsWholeContentThatExceedsExistingDatabaseLimit(){var d=bodyDocument("诊".repeat(4001));assertThatThrownBy(()->new ContextualKnowledgeChunker().chunk(d,new KnowledgeIndexProfile("E1",0,KnowledgeIndexProfile.Context.NONE),"test")).hasMessage("CHILD_EXCEEDS_DB_4000_CODEPOINTS");}
    @Test void rejectsZeroNonfiniteAndWrongDimensionVectors(){assertThatThrownBy(()->EmbeddingVectors.validate(new float[1024],1024)).hasMessage("EMBEDDING_ZERO_VECTOR");assertThatThrownBy(()->EmbeddingVectors.validate(new float[]{Float.NaN},1)).hasMessage("EMBEDDING_RESPONSE_INVALID");assertThatThrownBy(()->EmbeddingVectors.validate(new float[]{1},1024)).hasMessage("EMBEDDING_DIMENSION_MISMATCH");}
    static KnowledgeCorpus.Document bodyDocument(String body){var d=docs.getFirst();return new KnowledgeCorpus.Document(d.knowledgeId(),d.title(),d.domain(),d.category(),d.manufacturer(),d.deviceModels(),d.components(),d.applicableVersions(),d.sourceIds(),d.sourceLocations(),d.trustLevel(),d.verificationStatus(),d.sourceNature(),d.relatedSkills(),d.keywords(),KnowledgeCorpus.hash(body),body,body,0,d.originalMetadata(),d.sources(),d.preservedExtensionFields());}
}
