package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.ToIntFunction;

/** Recover only stored original children. Every returned part keeps its own citation identity. */
public final class ContextRecoveryService {
    public enum Strategy { NONE, ADJACENT, CONDITIONAL_PARENT, MERGE_HITS }
    public record Options(Strategy strategy,int maxChildren,int maxTokens,int maxParentTokens) {
        public Options {if(strategy==null||maxChildren<1||maxChildren>50||maxTokens<1||maxParentTokens<1)throw new IllegalArgumentException("CONTEXT_OPTIONS_INVALID");}
    }
    public record Parent(UUID id,UUID documentId,UUID releaseId,String owner,String project,String content,Map<UUID,StoredSectionBoundary.Span> spans) {
        public Parent {spans=Map.copyOf(spans);}
        public Parent(UUID id,UUID documentId,UUID releaseId,String owner,String project,String content){this(id,documentId,releaseId,owner,project,content,Map.of());}
    }
    public record Part(ScopedKnowledgeStore.Child child,String reason,int seedRank,int finalRank,int tokens) {}
    public record Block(UUID parentId,List<Part> parts,String content,boolean completeParent) {
        public Block {parts=List.copyOf(parts);}
    }
    public record Result(List<Part> parts,List<Block> blocks,int outputTokens,int budgetTokens,String tokenBasis,List<String> limitations) {
        public Result {parts=List.copyOf(parts);blocks=List.copyOf(blocks);limitations=List.copyOf(limitations);}
    }
    private final ToIntFunction<String> counter;
    private final String tokenBasis;
    public ContextRecoveryService(){this(s->s.getBytes(StandardCharsets.UTF_8).length,"UTF8_BYTE_TOKEN_UPPER_BOUND");}
    public ContextRecoveryService(ToIntFunction<String> counter,String tokenBasis){this.counter=Objects.requireNonNull(counter);this.tokenBasis=Objects.requireNonNull(tokenBasis);}
    public Result recover(ToolContext ctx,ScopedKnowledgeStore.Snapshot snapshot,KnowledgeSearchService.KnowledgeFilters filters,
                          ApplicabilityPolicy.Target target,List<HybridRetrievalService.Candidate> seeds,Map<UUID,Parent> parents,Options options) {
        if(!ctx.ownerId().equals(snapshot.owner())||!ctx.projectId().equals(snapshot.project()))throw new KnowledgeException("NOT_FOUND_OR_DENIED",404);
        var f=filters==null?KnowledgeSearchService.KnowledgeFilters.NONE:filters;var policy=new ApplicabilityPolicy();
        var authorized=new LinkedHashMap<UUID,ScopedKnowledgeStore.Child>();var groups=new LinkedHashMap<String,List<ScopedKnowledgeStore.Child>>();
        for(var c:snapshot.children()) {
            if(f.releaseId()!=null&&!f.releaseId().equals(c.releaseId())||f.domain()!=null&&!f.domain().equals(c.domain())||f.documentId()!=null&&!f.documentId().equals(c.documentId()))continue;
            if(c.metadata().isEmpty()&&f.deviceModel()!=null&&!f.deviceModel().equals(c.deviceModel()))continue;
            if(policy.assess(c.metadata(),target).status()==ApplicabilityPolicy.Status.NOT_APPLICABLE)continue;
            authorized.put(c.id(),c);groups.computeIfAbsent(group(c),k->new ArrayList<>()).add(c);
        }
        groups.values().forEach(g->g.sort(Comparator.comparingInt(ScopedKnowledgeStore.Child::chunkNo).thenComparing(c->c.id().toString())));
        for(var seed:seeds) {
            var c=authorized.get(seed.child().id());
            if(c==null||!c.equals(seed.child()))throw new KnowledgeException("CONTEXT_SEED_NOT_AUTHORIZED",404);
        }
        var sections=new HashMap<UUID,String>();
        for(var child:authorized.values()) {
            Parent parent=parents.get(child.parentId());if(parent!=null)validateParent(ctx,child,parent);
            String identity=StoredSectionBoundary.identity(child,parent);if(identity!=null)sections.put(child.id(),identity);
        }
        var byParent=new HashMap<String,Long>();for(var c:seeds)byParent.merge(group(c.child()),1L,Long::sum);
        var chosen=new LinkedHashMap<UUID,Part>();var textSeen=new HashSet<String>();var limits=new LinkedHashSet<String>();
        int used=0;
        for(int seedRank=0;seedRank<seeds.size();seedRank++) {
            var seed=seeds.get(seedRank).child();var siblings=groups.get(group(seed));
            var proposal=new ArrayList<ScopedKnowledgeStore.Child>();proposal.add(seed);String why="RETRIEVED";
            if(options.strategy()==Strategy.ADJACENT) {
                for(var c:siblings)if(Math.abs(c.chunkNo()-seed.chunkNo())==1&&sections.containsKey(seed.id())&&Objects.equals(sections.get(seed.id()),sections.get(c.id())))proposal.add(c);
                why="ADJACENT_SAME_PARENT_SECTION";
            }else if(options.strategy()==Strategy.CONDITIONAL_PARENT&&needsParent(seed,byParent.get(group(seed)))) {
                Parent parent=parents.get(seed.parentId());
                if(parent!=null)validateParent(ctx,seed,parent);
                boolean complete=parent!=null&&normalize(join(siblings)).equals(normalize(parent.content()));
                int cost=siblings.stream().mapToInt(c->count(c.title()+"\n"+c.content())+1).sum();
                if(complete&&cost<=options.maxParentTokens()) {proposal.clear();proposal.addAll(siblings);why="COMPLETE_STORED_PARENT_CHILDREN";}
                else limits.add(complete?"PARENT_TOO_LONG":"PARENT_INCOMPLETE_OR_INAPPLICABLE");
            }else if(options.strategy()==Strategy.MERGE_HITS) {
                for(var c:seeds)if(group(c.child()).equals(group(seed)))proposal.add(c.child());
                why="MERGED_RETRIEVED_CHILDREN";
            }
            // Retain the scored seed first; expansion never silently substitutes an unrelated earlier child.
            proposal.remove(seed);proposal.addFirst(seed);
            int extra=0,extraCount=0;var unique=new HashSet<UUID>();
            for(var c:proposal)if(!chosen.containsKey(c.id())&&unique.add(c.id())){extra+=count(c.title()+"\n"+c.content())+1;extraCount++;}
            if(why.equals("COMPLETE_STORED_PARENT_CHILDREN")&&(used+extra>options.maxTokens()||chosen.size()+extraCount>options.maxChildren())) {
                proposal.clear();proposal.add(seed);why="RETRIEVED";limits.add("COMPLETE_PARENT_BUDGET_REJECTED");
            }
            for(var c:proposal) {
                if(chosen.containsKey(c.id()))continue;
                // Equal wording in different sections can describe different components.
                String identity=group(c)+"|"+c.section()+"|"+KnowledgeCorpus.hash(c.content());if(textSeen.contains(identity))continue;
                int cost=count(c.title()+"\n"+c.content())+1;
                if(chosen.size()>=options.maxChildren()||used+cost>options.maxTokens()){limits.add("OUTPUT_BUDGET_REACHED");continue;}
                textSeen.add(identity);used+=cost;chosen.put(c.id(),new Part(c,c.id().equals(seed.id())?"RETRIEVED":why,seedRank+1,chosen.size()+1,cost));
            }
        }
        var blocks=new LinkedHashMap<String,List<Part>>();
        for(var p:chosen.values())blocks.computeIfAbsent(options.strategy()==Strategy.NONE?p.child().id().toString():group(p.child()),k->new ArrayList<>()).add(p);
        var resultBlocks=new ArrayList<Block>();
        for(var parts:blocks.values()) {
            var first=parts.getFirst().child();String content=join(parts.stream().map(Part::child).sorted(Comparator.comparingInt(ScopedKnowledgeStore.Child::chunkNo)).toList());
            Parent parent=parents.get(first.parentId());if(parent!=null)validateParent(ctx,first,parent);
            resultBlocks.add(new Block(first.parentId(),parts,content,parent!=null&&normalize(content).equals(normalize(parent.content()))));
        }
        return new Result(List.copyOf(chosen.values()),resultBlocks,used,options.maxTokens(),tokenBasis,List.copyOf(limits));
    }
    private int count(String s){int n=counter.applyAsInt(s);if(n<0)throw new IllegalArgumentException("CONTEXT_TOKEN_COUNT_INVALID");return n;}
    private static void validateParent(ToolContext ctx,ScopedKnowledgeStore.Child c,Parent p) {
        if(!ctx.ownerId().equals(p.owner())||!ctx.projectId().equals(p.project())||!Objects.equals(c.parentId(),p.id())||!c.documentId().equals(p.documentId())||!Objects.equals(c.releaseId(),p.releaseId()))
            throw new KnowledgeException("CONTEXT_PARENT_SCOPE_INVALID",404);
    }
    private static String group(ScopedKnowledgeStore.Child c){return c.releaseId()+"|"+c.documentId()+"|"+(c.parentId()==null?c.id():c.parentId());}
    private static String normalize(String s){return s.replaceAll("\\s+","");}
    private static String join(List<ScopedKnowledgeStore.Child> cs){return String.join("\n",cs.stream().map(ScopedKnowledgeStore.Child::content).toList());}
    private static boolean needsParent(ScopedKnowledgeStore.Child c,long hits) {
        return hits>1||((c.content()+" "+c.title()).matches("(?is).*(?:table|ROM|IGC|滤网|滤芯|恢复|续页|下表|表[0-9一二三四五]).*"));
    }
}
