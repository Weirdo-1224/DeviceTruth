package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RerankerTest {
    @TempDir Path temp;
    HttpServer server;ExecutorService executor;String response;int status=200;long delay;
    final AtomicInteger calls=new AtomicInteger();com.fasterxml.jackson.databind.JsonNode body;
    final UUID a=new UUID(0,1),b=new UUID(0,2);
    @BeforeEach void start()throws Exception {
        response="{\"request_id\":\"test\",\"usage\":{\"total_tokens\":30},\"output\":{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.2}]}}";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/rerank",x->{try{calls.incrementAndGet();body=Json.MAPPER.readTree(x.getRequestBody());if(delay>0)Thread.sleep(delay);byte[] bytes=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}catch(Exception ignored){}finally{x.close();}});server.start();
    }
    @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
    DashScopeRerankerClient client(int retries,long timeout){return new DashScopeRerankerClient(new DashScopeRerankerClient.Configuration(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/rerank"),"qwen3-rerank",null,Duration.ofMillis(timeout),retries),"TEST_ONLY_DUMMY_KEY");}
    RerankerClient.Request request(String instruction){return new RerankerClient.Request(new RerankerClient.Scope("owner","project","release"),"technical question",instruction,List.of(new RerankerClient.Document(a,"original A"),new RerankerClient.Document(b,"original B")));}
    void rows(String rows){response="{\"output\":{\"results\":"+rows+"},\"usage\":{\"total_tokens\":30}}";}
    @Test void nativeRequestAndResponseMapOriginalIndicesNotResponseOrder() {
        var c=client(0,2000);var r=c.score(request(RerankerClient.DOMAIN_INSTRUCTION));
        assertThat(r.scores().getFirst().chunkId()).isEqualTo(b);assertThat(r.scores().getLast().chunkId()).isEqualTo(a);
        assertThat(body.path("parameters").path("top_n").asInt()).isEqualTo(2);assertThat(body.path("parameters").path("instruct").asText()).isEqualTo(RerankerClient.DOMAIN_INSTRUCTION);
        assertThat(body.path("input").path("documents").get(0).asText()).isEqualTo("original A");assertThat(c.usage().reportedTokens()).isEqualTo(30);
    }
    @Test void defaultInstructionIsOmittedAndMissingUsageRemainsUnknown() {
        response=response.replace("\"usage\":{\"total_tokens\":30},","");var c=client(0,2000);assertThat(c.score(request("")).inputTokens()).isNull();
        assertThat(body.path("parameters").has("instruct")).isFalse();assertThat(c.usage().unknownUsageResponses()).isEqualTo(1);
    }
    @Test void duplicateIndicesRejectedWithoutRetry(){rows("[{\"index\":0,\"relevance_score\":0.3},{\"index\":0,\"relevance_score\":0.6}]");assertThatThrownBy(()->client(2,2000).score(request(""))).hasMessage("RERANK_INVALID_INDEX");assertThat(calls.get()).isEqualTo(1);}
    @Test void outOfRangeAndFractionalIndicesRejected() {
        for(String index:List.of("2","-1","0.5","\"0\"")){rows("[{\"index\":"+index+",\"relevance_score\":0.4},{\"index\":1,\"relevance_score\":0.5}]");assertThatThrownBy(()->client(0,2000).score(request(""))).isInstanceOf(RerankerException.class);}
    }
    @Test void missingScoresRejected(){rows("[{\"index\":0,\"relevance_score\":0.4}]");assertThatThrownBy(()->client(0,2000).score(request(""))).hasMessage("RERANK_MISSING_SCORES");}
    @Test void invalidScoresRejected() {
        for(String score:List.of("-0.1","1.1","null","\"NaN\"","true")){rows("[{\"index\":0,\"relevance_score\":"+score+"},{\"index\":1,\"relevance_score\":0.5}]");assertThatThrownBy(()->client(0,2000).score(request(""))).isInstanceOf(RerankerException.class);}
    }
    @Test void documentEchoMismatchRejected(){rows("[{\"index\":0,\"relevance_score\":0.4,\"document\":{\"text\":\"wrong chunk\"}},{\"index\":1,\"relevance_score\":0.5}]");assertThatThrownBy(()->client(0,2000).score(request(""))).hasMessage("RERANK_DOCUMENT_MISMATCH");}
    @Test void permissionErrorRedactedAndNotRetried(){status=403;response="{\"message\":\"SECRET_SUPPLIER_TEXT\"}";assertThatThrownBy(()->client(2,2000).score(request(""))).hasMessage("RERANK_AUTH_OR_PERMISSION_DENIED");assertThat(calls.get()).isEqualTo(1);}
    @Test void transientErrorRetriesAreBounded(){status=503;var c=client(1,2000);assertThatThrownBy(()->c.score(request(""))).hasMessage("RERANK_HTTP_503");assertThat(calls.get()).isEqualTo(2);assertThat(c.usage().retries()).isEqualTo(1);}
    @Test void timeoutIsBoundedAndRedacted(){delay=300;assertThatThrownBy(()->client(0,40).score(request(""))).hasMessage("RERANK_TIMEOUT_OR_IO");}
    @Test void emptyPoolAndLocalInputRejectionMakeNoRequest(){assertThat(client(0,1000).score(new RerankerClient.Request(request("").scope(),"q","",List.of())).scores()).isEmpty();assertThatThrownBy(()->client(0,1000).score(new RerankerClient.Request(request("").scope(),"q","",List.of(new RerankerClient.Document(a,"x".repeat(4001)))))).hasMessage("RERANK_INPUT_LIMIT");assertThat(calls.get()).isZero();}
    @Test void cacheSurvivesRestartAndSeparatesInstructionScopeContentModelAndPool()throws Exception {
        var c=client(0,2000);var cache=new RerankerScoreCache(c,temp);var r=request("");cache.score(r);assertThat(new RerankerScoreCache(c,temp).score(r).cacheHit()).isTrue();assertThat(calls.get()).isEqualTo(1);
        var keys=new HashSet<String>();keys.add(cache.key(r));keys.add(cache.key(request("domain")));
        keys.add(cache.key(new RerankerClient.Request(new RerankerClient.Scope("owner","other","release"),r.query(),"",r.documents())));
        keys.add(cache.key(new RerankerClient.Request(new RerankerClient.Scope("owner","project","other"),r.query(),"",r.documents())));
        keys.add(cache.key(new RerankerClient.Request(r.scope(),r.query(),"",List.of(new RerankerClient.Document(a,"changed"),r.documents().getLast()))));
        keys.add(cache.key(new RerankerClient.Request(r.scope(),r.query(),"",List.of(r.documents().getFirst()))));assertThat(keys).hasSize(6);
        var other=new RerankerClient(){public String configurationKey(){return "different-model";}public Response score(Request q){throw new AssertionError();}};
        assertThat(new RerankerScoreCache(other,temp).key(r)).isNotEqualTo(cache.key(r));
    }
    @Test void corruptCacheFailsExplicitly()throws Exception{var c=client(0,2000);var cache=new RerankerScoreCache(c,temp);var r=request("");cache.score(r);Files.writeString(temp.resolve(cache.key(r)+".json"),"{\"key\":\"corrupt\"}");assertThatThrownBy(()->new RerankerScoreCache(c,temp).score(r)).hasMessage("RERANK_CACHE_IDENTITY_INVALID");}
    @Test void scoreMappingAlsoValidatedForNonHttpClient() {
        var wrong=new RerankerClient.Response(List.of(new RerankerClient.Score(0,b,.5),new RerankerClient.Score(1,a,.5)),"x",0L,1,1,false);
        assertThatThrownBy(()->RerankerClient.validate(request(""),wrong)).hasMessage("RERANK_IDENTITY_MISMATCH");
    }
    @Test void unknownChargeReservationsPersistAndEnforceHardCap() {
        var ledger=temp.resolve("ledger.json");var budget=new RerankerBudget(ledger,new BigDecimal("0.01"),new BigDecimal("0.5"));
        budget.beforeAttempt("x",0,request(""),15000);budget.afterAttempt("x",0,null,503);
        var restarted=new RerankerBudget(ledger,new BigDecimal("0.01"),new BigDecimal("0.5"));
        assertThatThrownBy(()->restarted.beforeAttempt("y",0,request(""),15000)).hasMessage("RERANK_BUDGET_STOP");
        assertThat(restarted.summary().get("attempts")).isEqualTo(1);
    }
    @Test void deterministicTieOrderingAndFailurePreserveRrf() {
        var candidates=List.of(ContextRecoveryTest.candidate(b,1,1,"b"),ContextRecoveryTest.candidate(a,2,2,"a"));
        RerankerClient tie=new RerankerClient(){public String configurationKey(){return "tie";}public Response score(Request r){return new Response(List.of(new Score(1,a,.5),new Score(0,b,.5)),"x",0L,1,1,false);}};
        var result=new RerankingService(tie).rank(request("").scope(),"query","",candidates);assertThat(result.ranked()).extracting(x->x.candidate().child().id()).containsExactly(b,a);
        RerankerClient failure=new RerankerClient(){public String configurationKey(){return "failure";}public Response score(Request r){throw new RerankerException("RERANK_TIMEOUT_OR_IO");}};
        var fallback=new RerankingService(failure).rank(request("").scope(),"query","",candidates);assertThat(fallback.degraded()).isTrue();assertThat(fallback.reason()).isEqualTo("RERANK_TIMEOUT_OR_IO");assertThat(fallback.ranked()).extracting(x->x.candidate().child().id()).containsExactly(b,a);
    }
    @Test void unexpectedProviderRuntimeFailureIsRedactedAndKeepsRrf() {
        RerankerClient failed=new RerankerClient(){public String configurationKey(){return "runtime";}public Response score(Request r){throw new IllegalStateException("PRIVATE_SUPPLIER_DETAIL");}};
        var r=new RerankingService(failed).rank(request("").scope(),"query","",List.of(ContextRecoveryTest.candidate(a,1,1,"original")));
        assertThat(r.degraded()).isTrue();assertThat(r.reason()).isEqualTo("RERANK_UNAVAILABLE");assertThat(r.ranked().getFirst().candidate().child().id()).isEqualTo(a);
    }
}
