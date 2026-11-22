package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.cjk.*;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

/** Immutable versioned derived index. A validated directory is published by atomic rename. */
public final class LuceneBm25Index {
    public enum Analysis { CJK, SMARTCN, CJK_UNIGRAM_BIGRAM }
    public record Scored(UUID childId,double score) {}
    public record Report(String version,String fingerprint,int documents,String analyzer,float k1,float b,
                         boolean integrity,boolean reused,String path) {}
    public static final String VERSION="lucene-10.3.1-technical-v3";
    private final Path root;
    private final Analysis analysis;
    private final IdentifierExtractor identifiers=new IdentifierExtractor();
    public LuceneBm25Index(Path root){this(root,Analysis.CJK);}
    public LuceneBm25Index(Path root,Analysis analysis){this.root=root.toAbsolutePath().normalize();this.analysis=analysis;}
    public static Analyzer analyzer(Analysis kind) {
        return switch(kind) {
            case CJK -> new CJKAnalyzer(CharArraySet.EMPTY_SET);
            case SMARTCN -> new SmartChineseAnalyzer(false);
            case CJK_UNIGRAM_BIGRAM -> new Analyzer() {
                @Override protected TokenStreamComponents createComponents(String field) {
                    var tokenizer=new StandardTokenizer();
                    TokenStream stream=new CJKWidthFilter(tokenizer);stream=new LowerCaseFilter(stream);
                    stream=new CJKBigramFilter(stream,CJKBigramFilter.HAN|CJKBigramFilter.HIRAGANA|CJKBigramFilter.KATAKANA|CJKBigramFilter.HANGUL,true);
                    return new TokenStreamComponents(tokenizer,stream);
                }
            };
        };
    }
    public static List<String> tokens(Analysis kind,String text) {
        try(var a=analyzer(kind);var ts=a.tokenStream("text",text)) {
            var term=ts.addAttribute(CharTermAttribute.class);var values=new ArrayList<String>();ts.reset();
            while(ts.incrementToken())values.add(term.toString());ts.end();return List.copyOf(values);
        }catch(IOException e){throw new IllegalStateException("ANALYZER_FAILED",e);}
    }
    private Path directory(ScopedKnowledgeStore.Snapshot s) {
        String scope=KnowledgeCorpus.hash(s.owner()+"|"+s.project()+"|"+s.embeddingProfile());
        return root.resolve(scope).resolve(VERSION+"-"+analysis+"-"+s.fingerprint());
    }
    public Report build(ScopedKnowledgeStore.Snapshot snapshot) {
        Path target=directory(snapshot),temp=null;
        try {
            Files.createDirectories(target.getParent());
            try(var lockChannel=FileChannel.open(target.getParent().resolve("build.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=lockChannel.tryLock()) {
                if(lock==null)throw new KnowledgeException("LUCENE_BUILD_BUSY",409);
                if(Files.exists(target)) {
                    try{return validate(snapshot,true);}catch(KnowledgeException corrupt) {
                        Files.move(target,target.resolveSibling(target.getFileName()+".corrupt-"+UUID.randomUUID()),StandardCopyOption.ATOMIC_MOVE);
                    }
                }
                temp=Files.createTempDirectory(target.getParent(),"building-");
                try(var dir=FSDirectory.open(temp);var a=analyzer(analysis);
                    var writer=new IndexWriter(dir,new IndexWriterConfig(a).setOpenMode(IndexWriterConfig.OpenMode.CREATE).setSimilarity(new BM25Similarity(1.2f,0.75f)))) {
                    for(var child:snapshot.children()) {
                        var d=new Document();d.add(new StringField("child_id",child.id().toString(),Field.Store.YES));
                        d.add(new StringField("row_checksum",KnowledgeCorpus.hash(Json.write(child)),Field.Store.YES));
                        d.add(new StringField("owner",snapshot.owner(),Field.Store.NO));d.add(new StringField("project",snapshot.project(),Field.Store.NO));
                        d.add(new StringField("release",String.valueOf(child.releaseId()),Field.Store.NO));
                        String metadata=String.join(" ",metadataTerms(child));
                        d.add(new TextField("text",child.content()+"\n"+child.title()+"\n"+Objects.toString(child.section(),"")+"\n"+metadata,Field.Store.NO));
                        // Technical identifiers have their own untokenized field.
                        for(var id:identifiers.extract(child.content()+"\n"+child.title()+"\n"+metadata,identifiers.lexicon(child.metadata())))
                            d.add(new StringField("identifier",id.key(),Field.Store.NO));
                        writer.addDocument(d);
                    }
                    writer.setLiveCommitData(Map.of("version",VERSION,"analyzer",analysis.name(),"fingerprint",snapshot.fingerprint()).entrySet());writer.commit();
                }
                validateAt(snapshot,temp,false);
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);temp=null;
                return validate(snapshot,false);
            }
        }catch(KnowledgeException e){throw e;}catch(IOException|java.nio.channels.OverlappingFileLockException e){throw new KnowledgeException("LUCENE_BUILD_FAILED",503);}
        finally {
            if(temp!=null)try(var paths=Files.walk(temp)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(IOException ignored) { /* Incomplete directories are never readable generations. */ }
        }
    }
    private static List<String> metadataTerms(ScopedKnowledgeStore.Child c) {
        var terms=new ArrayList<String>();
        for(String key:List.of("device_models","components","keywords"))c.metadata().path(key).forEach(n->terms.add(n.asText()));
        terms.add(c.metadata().path("manufacturer").asText());return terms;
    }
    public Report validate(ScopedKnowledgeStore.Snapshot snapshot,boolean reused){return validateAt(snapshot,directory(snapshot),reused);}
    private Report validateAt(ScopedKnowledgeStore.Snapshot snapshot,Path path,boolean reused) {
        try(var dir=FSDirectory.open(path);var check=new CheckIndex(dir)) {
            if(!check.checkIndex().clean)throw new KnowledgeException("LUCENE_INTEGRITY_FAILED",503);
            try(var reader=DirectoryReader.open(dir)) {
                checkHeader(snapshot,reader);
                var expected=new TreeMap<String,String>();snapshot.children().forEach(c->expected.put(c.id().toString(),KnowledgeCorpus.hash(Json.write(c))));
                var actual=new TreeMap<String,String>();var stored=reader.storedFields();
                for(int i=0;i<reader.maxDoc();i++) {var d=stored.document(i);if(actual.put(d.get("child_id"),d.get("row_checksum"))!=null)throw new KnowledgeException("LUCENE_DUPLICATE_CHILD",503);}
                if(!expected.equals(actual))throw new KnowledgeException("LUCENE_PG_MISMATCH",503);
            }
            return new Report(VERSION,snapshot.fingerprint(),snapshot.children().size(),analysis.name(),1.2f,0.75f,true,reused,path.toString());
        }catch(KnowledgeException e){throw e;}catch(IOException e){throw new KnowledgeException("LUCENE_UNAVAILABLE",503);}
    }
    private void checkHeader(ScopedKnowledgeStore.Snapshot s,DirectoryReader reader)throws IOException {
        var m=reader.getIndexCommit().getUserData();
        if(!VERSION.equals(m.get("version")) || !analysis.name().equals(m.get("analyzer")) || !s.fingerprint().equals(m.get("fingerprint")) || reader.numDocs()!=s.children().size())
            throw new KnowledgeException("LUCENE_PG_MISMATCH",503);
    }
    public List<Scored> bm25(ScopedKnowledgeStore.Snapshot s,Set<UUID> eligible,String query,int limit) {
        var terms=new LinkedHashSet<>(tokens(analysis,query));if(terms.isEmpty())return List.of();
        if(terms.size()>512)throw KnowledgeException.invalid("query has too many lexical terms");
        var match=new BooleanQuery.Builder();for(String term:terms)match.add(new TermQuery(new Term("text",term)),BooleanClause.Occur.SHOULD);
        match.setMinimumNumberShouldMatch(Math.min(terms.size(),Math.max(1,(int)Math.ceil(terms.size()*0.20))));
        return search(s,eligible,match.build(),limit);
    }
    public List<Scored> identifier(ScopedKnowledgeStore.Snapshot s,Set<UUID> eligible,List<IdentifierExtractor.Identifier> ids,int limit) {
        if(ids.isEmpty())return List.of();var match=new BooleanQuery.Builder();
        for(var id:ids)match.add(new TermQuery(new Term("identifier",id.key())),BooleanClause.Occur.SHOULD);
        match.setMinimumNumberShouldMatch(1);return search(s,eligible,match.build(),limit);
    }
    private List<Scored> search(ScopedKnowledgeStore.Snapshot snapshot,Set<UUID> eligible,Query match,int limit) {
        if(eligible.isEmpty())return List.of();Path path=directory(snapshot);
        if(!Files.isDirectory(path))throw new KnowledgeException("LUCENE_INDEX_NOT_READY",503);
        try(var dir=FSDirectory.open(path);var reader=DirectoryReader.open(dir)) {
            checkHeader(snapshot,reader);var searcher=new IndexSearcher(reader);searcher.setSimilarity(new BM25Similarity(1.2f,0.75f));
            var query=new BooleanQuery.Builder().add(match,BooleanClause.Occur.MUST)
                    .add(new TermInSetQuery("child_id",eligible.stream().map(x->new BytesRef(x.toString())).toList()),BooleanClause.Occur.FILTER).build();
            // Fetch matches then break score ties by stable child UUID, independent of segment layout.
            var top=searcher.search(query,Math.max(1,reader.numDocs()));var stored=reader.storedFields();var result=new ArrayList<Scored>();
            for(var hit:top.scoreDocs)result.add(new Scored(UUID.fromString(stored.document(hit.doc).get("child_id")),hit.score));
            return result.stream().sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparing(x->x.childId().toString())).limit(limit).toList();
        }catch(IOException e){throw new KnowledgeException("LUCENE_UNAVAILABLE",503);}
    }
}
