package io.devicetruth.knowledge;

/** Collection, manufacturer versions, chunking, context, embedding and index identities stay separate. */
public record KnowledgeIndexProfile(String experiment, int targetTokens, Context context) {
    public enum Context { NONE, TITLE, METADATA }
    public static final String TOKENIZER="jtokkit-1.1.0-cl100k_base-proxy-not-qwen-billing";
    public KnowledgeIndexProfile {
        if(!java.util.Set.of("E0","E1","E2","E3").contains(experiment))throw KnowledgeCorpus.invalid("INDEX_EXPERIMENT_INVALID");
        if(targetTokens!=256 && targetTokens!=512 && targetTokens!=1024 && targetTokens!=0)throw KnowledgeCorpus.invalid("TOKEN_TARGET_INVALID");
        if(context==null || (!experiment.equals("E3") && context!=Context.NONE))throw KnowledgeCorpus.invalid("CONTEXT_PROFILE_INVALID");
    }
    public String chunkingProfile(){return experiment.equals("E0")?"section-paragraph-v1-480cp":
            (experiment.equals("E1")||targetTokens==0?"whole-document-v1":"paragraph-topic-v1-t"+targetTokens+"-cl100k");}
    public String id(){return experiment+"-"+chunkingProfile()+"-"+context.name()+"-r1";}
}
