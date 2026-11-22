package io.devicetruth.knowledge;

import java.util.*;

/** Weighted 1-based RRF. Missing ranks contribute zero; scores are not probabilities. */
public final class ReciprocalRankFusion {
    public record Ranked(UUID childId,double score,Integer denseRank,Integer bm25Rank,Integer identifierRank) {}
    public static List<Ranked> fuse(List<UUID> dense,List<UUID> bm25,List<UUID> identifiers,
                                    int k,double denseWeight,double bm25Weight,double identifierWeight) {
        if(k<1 || java.util.stream.DoubleStream.of(denseWeight,bm25Weight,identifierWeight).anyMatch(x->!Double.isFinite(x)||x<0))
            throw new IllegalArgumentException("invalid RRF configuration");
        var ranks=new TreeMap<String,int[]>();var lists=List.of(dense,bm25,identifiers);double[] weights={denseWeight,bm25Weight,identifierWeight};
        for(int channel=0;channel<3;channel++) {
            var seen=new HashSet<UUID>();int rank=0;
            for(UUID id:lists.get(channel))if(seen.add(id))ranks.computeIfAbsent(id.toString(),s->new int[3])[channel]=++rank;
        }
        var out=new ArrayList<Ranked>();
        for(var entry:ranks.entrySet()) {int[] r=entry.getValue();double score=0;
            for(int i=0;i<3;i++)if(r[i]!=0)score+=weights[i]/(k+r[i]);
            if(score>0)out.add(new Ranked(UUID.fromString(entry.getKey()),score,nullable(r[0]),nullable(r[1]),nullable(r[2])));
        }
        return out.stream().sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(r->r.childId().toString())).toList();
    }
    private static Integer nullable(int n){return n==0?null:n;}
    private ReciprocalRankFusion(){}
}
