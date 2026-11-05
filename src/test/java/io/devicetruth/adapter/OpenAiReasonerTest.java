package io.devicetruth.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.reasoning.ScriptedReasoner;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.state.DiagnosisState;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 只请求本机 HTTP 替身，验证真实 SDK 请求格式与整个 Core；不是在线模型验收。 */
class OpenAiReasonerTest {
    private HttpServer server;
    private final List<Request> requests=new CopyOnWriteArrayList<>();
    private Function<JsonNode,Reply> reply;
    private record Request(String method,String path,String authorization,JsonNode body) {}
    private record Reply(int status,String body) {}

    @BeforeEach void startServer() throws Exception {
        reply=body->ok("{\"type\":\"ESCALATE\",\"reason\":\"local protocol check\"}");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try {
                var body=Json.MAPPER.readTree(exchange.getRequestBody());
                requests.add(new Request(exchange.getRequestMethod(),exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),body));
                var response=reply.apply(body);
                byte[] bytes=response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(response.status(),bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stopServer() { server.stop(0); }

    private Map<String,String> environment(String path) {
        return Map.of("OPENAI_API_KEY","local-test-secret","OPENAI_BASE_URL",
                "http://127.0.0.1:"+server.getAddress().getPort()+path,"OPENAI_MODEL","offline-model");
    }

    private Reasoner.Context context() {
        return new Reasoner.Context(DiagnosisState.initial("local-task",new DiagnosisRequest("fixture","generic","TIMEOUT",
                Instant.parse("2026-11-03T04:00:00Z")),Budget.defaults()),List.of(),List.of());
    }

    private static Reply ok(String content) {
        return new Reply(200,Json.write(Map.of("id","local-response","object","chat.completion","created",0,
                "model","offline-model","choices",List.of(Map.of("index",0,"finish_reason","stop",
                        "message",Map.of("role","assistant","content",content))))));
    }

    @Test void sendsChatCompletionsJsonAndPreservesConfiguredBasePaths() {
        for(String path:List.of("","/v1/","/proxy/v2")) {
            var reasoner=new OpenAiReasoner(environment(path)::get);
            assertThat(reasoner.propose(context())).isInstanceOf(ActionProposal.Escalate.class);
            assertThat(reasoner.name()).isEqualTo("openai");
            assertThat(reasoner.drainAttempts()).extracting(Reasoner.Attempt::outcome).containsExactly("VALID_PROPOSAL");
        }
        assertThat(requests).extracting(Request::path).containsExactly("/chat/completions","/v1/chat/completions","/proxy/v2/chat/completions");
        for(var request:requests) {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.authorization()).isEqualTo("Bearer local-test-secret");
            assertThat(request.body().path("model").asText()).isEqualTo("offline-model");
            assertThat(request.body().path("stream").asBoolean()).isFalse();
            assertThat(request.body().at("/response_format/type").asText()).isEqualTo("json_object");
            assertThat(request.body().has("tools")).isFalse();
            assertThat(request.body().at("/messages/0/role").asText()).isEqualTo("system");
            assertThat(request.body().at("/messages/1/content").asText()).contains("\"version\":0");
            assertThat(request.body().toString()).doesNotContain("local-test-secret");
        }
    }

    @Test void missingAndInvalidConfigurationFailsBeforeAnyRequest() {
        for(String name:List.of("OPENAI_API_KEY","OPENAI_BASE_URL","OPENAI_MODEL")) {
            var env=new HashMap<>(environment("/v1"));env.put(name," ");
            var reasoner=new OpenAiReasoner(env::get);
            assertThatThrownBy(()->reasoner.propose(context())).hasMessage(name+"_MISSING");
            assertThat(reasoner.drainAttempts()).extracting(Reasoner.Attempt::outcome).containsExactly(name+"_MISSING");
        }
        for(String base:List.of("not-a-url","file:///tmp/test","https://user:secret@example.com/v1",
                "https://example.com/v1?key=secret","https://example.com/v1#secret","https://example.com/v1/chat/completions/")) {
            var env=new HashMap<>(environment(""));env.put("OPENAI_BASE_URL",base);
            assertThatThrownBy(()->new OpenAiReasoner(env::get).propose(context())).hasMessage("OPENAI_CONFIGURATION_INVALID");
        }
        assertThat(requests).isEmpty();
    }

    @Test void invalidActionsRetryOnceAndPersistentFailureStops() {
        reply=body->ok("not JSON");
        var reasoner=new OpenAiReasoner(environment("/v1")::get);
        assertThatThrownBy(()->reasoner.propose(context())).hasMessage("MODEL_OUTPUT_INVALID_AFTER_RETRY");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).body().at("/messages/0/content").asText()).contains("Previous output was invalid");
        assertThat(reasoner.drainAttempts()).extracting(Reasoner.Attempt::outcome)
                .containsExactly("INVALID_JSON_OR_SCHEMA","INVALID_JSON_OR_SCHEMA");
    }

    @Test void httpFailuresDoNotRetryOrExposeProviderBodyInReports() throws Exception {
        reply=body->new Reply(401,"{\"error\":{\"message\":\"local-test-secret private-provider-detail\"}}");
        var repository=JsonRepositories.fromClasspath();
        var result=new TaskOrchestrator(CapabilityGateway.standard(repository,repository,repository),new EvidenceManager(),new DecisionGuard())
                .run(context().state().task(),Budget.defaults(),new OpenAiReasoner(environment("/v1")::get));
        assertThat(requests).hasSize(1);
        assertThat(result.result().runStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(result.result().outcome()).isNull();
        assertThat(result.result().stopReason()).isEqualTo("MODEL_TRANSPORT_OR_TIMEOUT");
        assertThat(Json.write(result)).doesNotContain("local-test-secret","private-provider-detail");
    }

    @Test void localHttpModelDrivesScenarioBThroughGatewayEvidenceAndGuard(@TempDir Path directory) throws Exception {
        var scripted=new ScriptedReasoner();
        reply=body->{
            try {
                var context=Json.MAPPER.readValue(body.at("/messages/1/content").asText(),Reasoner.Context.class);
                return ok(Json.write(scripted.propose(context)));
            } catch(Exception e) { throw new IllegalStateException(e); }
        };
        var repository=JsonRepositories.fromClasspath();
        Path configuration=directory.resolve("devicetruth.local.properties");
        StringBuilder content=new StringBuilder();
        environment("/v1").forEach((name,value)->content.append(name).append('=').append(value).append('\n'));
        Files.writeString(configuration,content);
        var task=new DiagnosisRequest("CAR-B","automotive-demo","COMM_TIMEOUT",Instant.parse("2026-11-03T04:00:00Z"));
        var report=new TaskOrchestrator(CapabilityGateway.standard(repository,repository,repository),new EvidenceManager(),new DecisionGuard())
                .run(task,Budget.defaults(),new OpenAiReasoner(new ModelConfiguration(configuration,name->null)));
        assertThat(report.result().runStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(report.result().outcome()).isEqualTo(Outcome.RESOLVED);
        assertThat(report.result().appliedRuleIds()).containsExactly("DEMO-D-120B");
        assertThat(report.result().reasoner()).isEqualTo("openai");
        assertThat(requests).hasSize(7);
        assertThat(report.events().stream().filter(e->e.type().equals("MODEL_ATTEMPT"))).hasSize(7);
        assertThat(Json.write(report)).doesNotContain("local-test-secret");
    }
}
