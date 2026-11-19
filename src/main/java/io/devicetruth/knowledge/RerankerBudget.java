package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Durable worst-case reservation before each HTTP attempt; unknown usage retains its reservation. */
public final class RerankerBudget implements DashScopeRerankerClient.Observer {
    private final Path path;
    private final BigDecimal cap,rate;
    private final List<Map<String,Object>> events=new ArrayList<>();
    public RerankerBudget(Path path,BigDecimal capCny,BigDecimal cnyPerMillionTokens) {
        this.path=path;this.cap=capCny;this.rate=cnyPerMillionTokens.movePointLeft(6);
        if(cap.signum()<=0||rate.signum()<=0)throw new IllegalArgumentException("RERANK_BUDGET_INVALID");
        try {
            if(Files.exists(path)) {
                var json=Json.MAPPER.readTree(path.toFile());
                if(cap.compareTo(new BigDecimal(json.path("budget_cny").asText()))!=0||rate.compareTo(new BigDecimal(json.path("rate_cny_per_token").asText()))!=0)
                    throw new RerankerException("RERANK_BUDGET_POLICY_CHANGED");
                for(var e:json.path("events"))events.add(Json.MAPPER.convertValue(e,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){}));
            }
            check(0);persist();
        }catch(RerankerException e){throw e;}catch(Exception e){throw new RerankerException("RERANK_BUDGET_LEDGER_UNAVAILABLE");}
    }
    public synchronized void beforeAttempt(String id,int attempt,RerankerClient.Request r,long upper) {
        check(upper);var event=new LinkedHashMap<String,Object>();event.put("request_id",id);event.put("attempt",attempt);event.put("time",Instant.now().toString());
        event.put("token_upper",upper);event.put("provider_tokens",null);event.put("status","RESERVED");event.put("http_status",null);
        event.put("query_hash",KnowledgeCorpus.hash(r.query()));event.put("instruction_hash",KnowledgeCorpus.hash(r.instruction()));event.put("document_count",r.documents().size());
        events.add(event);persist();
    }
    public synchronized void afterAttempt(String id,int attempt,Long tokens,Integer http) {
        var e=events.stream().filter(x->id.equals(x.get("request_id"))&&((Number)x.get("attempt")).intValue()==attempt).reduce((a,b)->b).orElseThrow(()->new RerankerException("RERANK_BUDGET_RESERVATION_MISSING"));
        e.put("provider_tokens",tokens);e.put("http_status",http);e.put("status",tokens==null?"UNKNOWN_CHARGE_RESERVED":"USAGE_REPORTED");persist();check(0);
    }
    private long accounted(){return events.stream().mapToLong(e->((Number)(e.get("provider_tokens")==null?e.get("token_upper"):e.get("provider_tokens"))).longValue()).sum();}
    private void check(long more){if(rate.multiply(BigDecimal.valueOf(accounted()+more)).compareTo(cap)>0)throw new RerankerException("RERANK_BUDGET_STOP");}
    public synchronized Map<String,Object> summary() {
        long known=events.stream().filter(e->e.get("provider_tokens")!=null).mapToLong(e->((Number)e.get("provider_tokens")).longValue()).sum();
        return Map.of("budget_cny",cap.toPlainString(),"rate_cny_per_token",rate.toPlainString(),"attempts",events.size(),"known_provider_tokens",known,
                "known_reference_cost_cny",rate.multiply(BigDecimal.valueOf(known)).toPlainString(),"accounted_upper_cost_cny",rate.multiply(BigDecimal.valueOf(accounted())).toPlainString(),"events",events);
    }
    private void persist() {
        try{Files.createDirectories(path.toAbsolutePath().getParent());Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"rerank-budget-",".tmp");
            try{Files.writeString(temp,Json.write(summary()));Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
        }catch(Exception e){throw new RerankerException("RERANK_BUDGET_WRITE_FAILED");}
    }
}
