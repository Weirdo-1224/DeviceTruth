package io.devicetruth.knowledge;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Complete document parent, exact contiguous child spans, atomic paragraphs/tables/identifiers. */
public final class ContextualKnowledgeChunker {
    private static final Encoding ENCODING=Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    public record Child(UUID chunkId,UUID parentId,String knowledgeId,int chunkNo,String sectionPath,String content,
                        String contentChecksum,String embeddingText,String embeddingChecksum,int bodyStart,int bodyEnd,
                        UUID previousChunkId,UUID nextChunkId,int contentTokens,int embeddingTokens,boolean exceedsTarget) {}
    public static int tokens(String text){return ENCODING.countTokens(text);}
    public List<Child> chunk(KnowledgeCorpus.Document doc,KnowledgeIndexProfile profile,String identityScope) {
        String body=doc.body();var spans=new ArrayList<int[]>();
        if(profile.experiment().equals("E0")) {
            // Keep the existing chunker as baseline; find literal spans without altering its algorithm.
            int cursor=0;
            for(var c:KnowledgeChunker.of(body,true,480)) {
                int start=body.indexOf(c.content(),cursor);
                int end=start+c.content().length();
                if(start<0) {
                    // Legacy parser strips line indentation; map the same non-whitespace text back to raw spans.
                    String regex=c.content().codePoints().filter(cp->!Character.isWhitespace(cp))
                            .mapToObj(cp->Pattern.quote(new String(Character.toChars(cp)))).collect(java.util.stream.Collectors.joining("\\s*"));
                    var mapped=Pattern.compile(regex).matcher(body);
                    if(!mapped.find(cursor))throw KnowledgeCorpus.invalid("LEGACY_CHUNK_SPAN_UNMAPPABLE");
                    start=mapped.start();end=mapped.end();
                }
                spans.add(new int[]{start,end});cursor=end;
            }
        } else if(profile.experiment().equals("E1") || profile.targetTokens()==0 || tokens(body)<=profile.targetTokens())spans.add(new int[]{0,body.length()});
        else {
            // Paragraphs (including Markdown tables) are indivisible. Soft token overruns are reported.
            var boundaries=new ArrayList<Integer>();boundaries.add(0);
            var matcher=Pattern.compile("\\r?\\n[ \\t]*\\r?\\n+").matcher(body);
            while(matcher.find()) {
                String previous=body.substring(boundaries.getLast(),matcher.start()).strip();
                if(!previous.matches("#{1,6}[^\\r\\n]+"))boundaries.add(matcher.end());
            }
            boundaries.add(body.length());
            int start=0,end=0;
            for(int i=1;i<boundaries.size();i++) {
                int next=boundaries.get(i);
                if(end>start && tokens(body.substring(start,next))>profile.targetTokens()) {spans.add(new int[]{start,end});start=end;}
                end=next;
            }
            if(end>start)spans.add(new int[]{start,end});
        }
        UUID parent=KnowledgeCorpus.stableId(identityScope+"|parent|"+doc.knowledgeId()+"|"+doc.originalChecksum());
        var ids=new ArrayList<UUID>();
        for(int[] s:spans)ids.add(KnowledgeCorpus.stableId(identityScope+"|"+profile.id()+"|"+doc.knowledgeId()+"|"+doc.originalChecksum()+"|"+s[0]+":"+s[1]));
        var result=new ArrayList<Child>();
        for(int i=0;i<spans.size();i++) {
            int[] s=spans.get(i);String content=body.substring(s[0],s[1]);
            if(content.codePointCount(0,content.length())>4000)throw KnowledgeCorpus.invalid("CHILD_EXCEEDS_DB_4000_CODEPOINTS");
            String section=section(body,s[0]);String prefix=prefix(doc,section,profile.context());
            String enhanced=prefix.isBlank()?content:prefix+"\n\n"+content;
            int inputTokens=tokens(enhanced);
            if(inputTokens>7000)throw KnowledgeCorpus.invalid("EMBEDDING_PROXY_TOKEN_LIMIT_EXCEEDED");
            result.add(new Child(ids.get(i),parent,doc.knowledgeId(),i+1,section,content,KnowledgeCorpus.hash(content),enhanced,
                    KnowledgeCorpus.hash(enhanced),s[0],s[1],i==0?null:ids.get(i-1),i+1==ids.size()?null:ids.get(i+1),tokens(content),
                    inputTokens,profile.targetTokens()>0 && tokens(content)>profile.targetTokens()));
        }
        return List.copyOf(result);
    }
    private static String section(String body,int start) {
        var headings=new ArrayList<String>();var m=Pattern.compile("(?m)^(#{1,6})[ \\t]+([^\\r\\n]+)").matcher(body);
        while(m.find() && m.start()<=start) {
            int level=m.group(1).length();while(headings.size()>=level)headings.removeLast();
            while(headings.size()<level-1)headings.add("");headings.add(m.group(2));
        }
        return headings.stream().filter(s->!s.isBlank()).reduce((a,b)->a+" / "+b).orElse("正文");
    }
    public static String prefix(KnowledgeCorpus.Document d,String section,KnowledgeIndexProfile.Context context) {
        if(context==KnowledgeIndexProfile.Context.NONE)return "";
        String p="标题："+d.title()+"\n章节："+section;
        if(context==KnowledgeIndexProfile.Context.METADATA)p="领域："+d.domain()+"\n厂商："+d.manufacturer()+"\n型号："+String.join("；",d.deviceModels())+
                "\n适用版本声明："+String.join("；",d.applicableVersions())+"\n部件："+String.join("；",d.components())+"\n类别："+d.category()+"\n"+p;
        if(p.contains("http://")||p.contains("https://"))throw KnowledgeCorpus.invalid("CONTEXT_CONTAINS_URL");
        return p;
    }
}
