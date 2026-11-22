package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Native DashScope text-rerank API; no framework or embedding dependency. */
public final class DashScopeRerankerClient implements RerankerClient {
    public record Configuration(URI endpoint,String model,String workspace,Duration timeout,int retries) {
        public Configuration {
            Objects.requireNonNull(endpoint);Objects.requireNonNull(timeout);
            if(!"https".equals(endpoint.getScheme())&&!isLoopback(endpoint))throw new IllegalArgumentException("RERANK_HTTPS_REQUIRED");
            if(endpoint.getHost()==null||endpoint.getUserInfo()!=null||endpoint.getQuery()!=null||endpoint.getFragment()!=null
                    ||model==null||!model.matches("[A-Za-z0-9._-]{1,128}")||timeout.toMillis()<1||timeout.toMillis()>120000||retries<0||retries>2
                    ||workspace!=null&&!workspace.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("RERANK_CONFIG_INVALID");
        }
        private static boolean isLoopback(URI u){return "http".equals(u.getScheme())&&Set.of("localhost","127.0.0.1","[::1]").contains(u.getHost());}
        public static Configuration beijing(){return new Configuration(URI.create("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"),"qwen3-rerank",null,Duration.ofSeconds(30),1);}
    }
    public interface Observer {
        default void beforeAttempt(String id,int attempt,Request request,long tokenUpper){}
        default void afterAttempt(String id,int attempt,Long tokens,Integer status){}
    }
    public record Usage(long requests,long successfulResponses,long retries,long reportedTokens,long unknownUsageResponses) {}
    private final Configuration config;
    private final String apiKey;
    private final HttpClient http;
    private final Observer observer;
    private final AtomicLong requests=new AtomicLong(),success=new AtomicLong(),retried=new AtomicLong(),tokens=new AtomicLong(),unknown=new AtomicLong();
    public DashScopeRerankerClient(Configuration config,String apiKey){this(config,apiKey,new Observer(){});}
    public DashScopeRerankerClient(Configuration config,String apiKey,Observer observer) {
        this.config=Objects.requireNonNull(config);this.apiKey=apiKey==null?"":apiKey;this.observer=Objects.requireNonNull(observer);
        this.http=HttpClient.newBuilder().connectTimeout(config.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public String configurationKey(){return KnowledgeCorpus.hash("dashscope-native-v1|"+config.endpoint()+"|"+config.model()+"|"+config.workspace());}
    public Usage usage(){return new Usage(requests.get(),success.get(),retried.get(),tokens.get(),unknown.get());}
    /** UTF8 bytes upper-bound BPE token count; repeated query/instruction included per pair. */
    public static long tokenUpper(Request r) {
        long query=bytes(r.query())+bytes(r.instruction())+128L;
        return r.documents().stream().mapToLong(d->query+bytes(d.text())+64L).sum();
    }
    private static int bytes(String s){return s.getBytes(StandardCharsets.UTF_8).length;}
    public Response score(Request r) {
        if(r.documents().isEmpty())return new Response(List.of(),"EMPTY",0L,0,0,false);
        if(apiKey.isBlank())throw new RerankerException("RERANK_API_KEY_MISSING");
        // Conservative local limits reject overlong inputs instead of truncating original evidence.
        if(bytes(r.query())>4000||r.documents().stream().anyMatch(d->bytes(d.text())>4000)||tokenUpper(r)>120000)
            throw new RerankerException("RERANK_INPUT_LIMIT");
        var params=new LinkedHashMap<String,Object>();params.put("top_n",r.documents().size());params.put("return_documents",false);
        if(!r.instruction().isBlank())params.put("instruct",r.instruction());
        String body=Json.write(Map.of("model",config.model(),"input",Map.of("query",r.query(),"documents",r.documents().stream().map(Document::text).toList()),"parameters",params));
        long started=System.nanoTime();String id=UUID.randomUUID().toString();
        for(int attempt=0;attempt<=config.retries();attempt++) {
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                throw new RerankerException("RERANK_EXTERNAL_CALL_IN_TRANSACTION");
            observer.beforeAttempt(id,attempt,r,tokenUpper(r));requests.incrementAndGet();if(attempt>0)retried.incrementAndGet();
            Integer status=null;Long usage=null;boolean recorded=false;
            try {
                var builder=HttpRequest.newBuilder(config.endpoint()).timeout(config.timeout()).header("Content-Type","application/json")
                        .header("Authorization","Bearer "+apiKey).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
                if(config.workspace()!=null)builder.header("X-DashScope-WorkSpace",config.workspace());
                var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));status=response.statusCode();
                com.fasterxml.jackson.databind.JsonNode json=null;
                try{json=Json.MAPPER.readTree(response.body());}catch(Exception ignored){}
                if(json!=null&&json.path("usage").path("total_tokens").isIntegralNumber()&&json.path("usage").path("total_tokens").canConvertToLong()) {
                    usage=json.path("usage").path("total_tokens").longValue();if(usage<0)usage=null;
                }
                observer.afterAttempt(id,attempt,usage,status);recorded=true;
                if(usage==null)unknown.incrementAndGet();else tokens.addAndGet(usage);
                if(status!=200) {
                    if((status==429||status>=500)&&attempt<config.retries()){pause(attempt);continue;}
                    throw new RerankerException(status==401||status==403?"RERANK_AUTH_OR_PERMISSION_DENIED":"RERANK_HTTP_"+status);
                }
                if(json==null||json.hasNonNull("code"))throw new RerankerException("RERANK_RESPONSE_INVALID");
                var rows=json.path("output").path("results");if(!rows.isArray())throw new RerankerException("RERANK_RESPONSE_INVALID");
                var scores=new ArrayList<Score>();
                for(var row:rows) {
                    var index=row.path("index");var score=row.path("relevance_score");
                    if(!index.isIntegralNumber()||!index.canConvertToInt()||!score.isNumber())throw new RerankerException("RERANK_RESULT_TYPE_INVALID");
                    int i=index.intValue();if(i<0||i>=r.documents().size())throw new RerankerException("RERANK_INVALID_INDEX");
                    if(row.has("document")&&!r.documents().get(i).text().equals(row.path("document").path("text").asText()))throw new RerankerException("RERANK_DOCUMENT_MISMATCH");
                    scores.add(new Score(i,r.documents().get(i).chunkId(),score.doubleValue()));
                }
                var result=new Response(scores,json.path("request_id").asText("UNKNOWN"),usage,attempt+1,(System.nanoTime()-started)/1_000_000,false);
                RerankerClient.validate(r,result);success.incrementAndGet();return result;
            }catch(InterruptedException e){Thread.currentThread().interrupt();if(!recorded)observer.afterAttempt(id,attempt,usage,status);throw new RerankerException("RERANK_INTERRUPTED");}
            catch(java.io.IOException e){if(!recorded)observer.afterAttempt(id,attempt,usage,status);if(attempt<config.retries()){pause(attempt);continue;}throw new RerankerException("RERANK_TIMEOUT_OR_IO");}
            catch(IllegalArgumentException e){if(!recorded)observer.afterAttempt(id,attempt,usage,status);throw new RerankerException("RERANK_REQUEST_INVALID");}
        }
        throw new RerankerException("RERANK_UNAVAILABLE");
    }
    private static void pause(int attempt){try{Thread.sleep(100L*(attempt+1));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RerankerException("RERANK_INTERRUPTED");}}
}
