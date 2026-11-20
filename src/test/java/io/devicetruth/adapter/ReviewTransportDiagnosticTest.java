package io.devicetruth.adapter;

import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.reasoning.ActionProposal;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 隔离模拟样本的显式传输诊断，不安装到产品连接。 */
class ReviewTransportDiagnosticTest {
    @Test void observeFullReviewResponseProgress() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.review-transport"));
        var folder = Path.of("outputs/validation/authenticated-real-model");
        String id = "24d4fd8a-303b-4159-ae84-2a9c04939626";
        var report = Json.MAPPER.readTree(Files.readString(folder.resolve("report-" + id + ".json")));
        ActionProposal.Answer draft = null;
        for (var sample : Json.MAPPER.readTree(Files.readString(folder.resolve("sample-" + id + ".json")))) {
            try {
                var action = Json.MAPPER.readValue(sample.path("response").asText(), ActionProposal.class);
                if (action instanceof ActionProposal.Answer answer) draft = answer;
            } catch (Exception ignored) { }
        }
        assertThat(draft).isNotNull();
        var refs = new ArrayList<Reference>();
        for (var ref : report.path("references")) refs.add(Json.MAPPER.treeToValue(ref, Reference.class));
        String goal = "这是模拟资料问答，不是现场故障确诊。请检索本空间手册中“直流快充中断”的章节，说明优先读取哪些记录、怎样核对车辆与充电桩时间，引用实际取得的资料。已确认是直流快充，SOC约40%，没有现场日志或设备档案，不要捏造实测或配置。";
        Instant at = Instant.parse(report.path("run").path("asOf").asText());
        var task = new AgentTaskInput("owner", "project", UUID.randomUUID(), UUID.fromString(id), UUID.randomUUID(),
                goal, List.of(new AgentTaskInput.Message("USER", goal)), null, at, at, 0, 0, 12);
        var captured = new String[2];
        var capture = new StructuredAgentReasoner("capture", (system, input) -> {
            captured[0] = system; captured[1] = input; return "{}";
        });
        try { capture.reviewAnswer(task, refs, draft, () -> null, () -> 45000); } catch (RuntimeException expected) { }
        var config = ModelConfiguration.local();
        var payload = new LinkedHashMap<String,Object>(Map.of("model", config.apply("OPENAI_MODEL"), "stream", true, "temperature", 0,
                "response_format", Map.of("type", "json_object"), "messages", List.of(
                        Map.of("role", "system", "content", captured[0]), Map.of("role", "user", "content", captured[1]))));
        boolean disabled = Boolean.getBoolean("devicetruth.test.disable-thinking");
        if (disabled) payload.put("thinking", Map.of("type", "disabled"));
        var request = HttpRequest.newBuilder(URI.create(config.apply("OPENAI_BASE_URL").replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + config.apply("OPENAI_API_KEY"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(payload))).build();
        var metrics = new LinkedHashMap<String, Object>();
        long started = System.nanoTime();
        long chars = 0, blank = 0, reasoning = 0, chunks = 0;
        var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            metrics.put("headersMs", (System.nanoTime() - started) / 1000000);
            metrics.put("status", response.statusCode());
            assertThat(response.statusCode()).isEqualTo(200);
            try (var body = response.body()) {
                timer.schedule(() -> { try { body.close(); } catch (Exception ignored) { } },
                        Math.max(1, 45000 - (System.nanoTime() - started) / 1000000), java.util.concurrent.TimeUnit.MILLISECONDS);
                var reader = new java.io.BufferedReader(new java.io.InputStreamReader(body, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    if (line.substring(5).trim().equals("[DONE]")) { metrics.put("done", true); break; }
                    var node = Json.MAPPER.readTree(line.substring(5).trim());
                    var delta = node.path("choices").path(0).path("delta");
                    String text = delta.path("content").asText("");
                    if (!text.isEmpty() && chars == 0) metrics.put("firstContentMs", (System.nanoTime() - started) / 1000000);
                    chars += text.length(); blank += text.chars().filter(Character::isWhitespace).count();
                    reasoning += delta.path("reasoning_content").asText("").length(); chunks++;
                    var finish = node.path("choices").path(0).path("finish_reason");
                    if (!finish.isNull() && !finish.isMissingNode()) metrics.put("finishReason", finish.asText());
                }
            }
        } catch (Exception failure) { metrics.put("exceptionType", failure.getClass().getSimpleName()); }
        finally {
            timer.shutdownNow();
            metrics.put("durationMs", (System.nanoTime() - started) / 1000000);
            metrics.put("contentChars", chars); metrics.put("whitespaceChars", blank);
            metrics.put("reasoningChars", reasoning); metrics.put("chunks", chunks);
            Files.writeString(folder.resolve(disabled ? "full-review-transport-disabled.json" : "full-review-transport.json"), Json.write(metrics));
        }
    }
}
