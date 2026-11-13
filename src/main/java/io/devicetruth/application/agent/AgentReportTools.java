package io.devicetruth.application.agent;

import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.application.agent.GeneralAgentService.MessageView;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用调查报告工具（03-02）：把当前 run 的持久事实聚合成只读报告供模型引用与转述。
 * 数据全部来自本 run 已持久化的白名单视图（消息计数、引用、工具轨迹、预算、待答问题/答案摘要），
 * 推理输入、模型原文、密钥与内部配置永不进入报告。工具只读，且归属校验沿用仓储层：
 * 跨 owner/project 的 runId 取不到任何内容。
 */
public final class AgentReportTools {
    public static final String REPORT = "generate_diagnosis_report";

    public record ToolDescriptor(String name, String description, String domain, boolean readOnly,
                                 Map<String, Object> inputSchema, String outputSchema) {}

    private final AgentRunRepository runs;
    private final ConversationRepository conversations;

    public AgentReportTools(AgentRunRepository runs, ConversationRepository conversations) {
        this.runs = runs;
        this.conversations = conversations;
    }

    /** 注册进产品 Agent 网关：注册即服务器授权，模型只能按公开 schema 调用。 */
    public void registerInto(CapabilityGateway gateway) {
        ToolDescriptor descriptor = descriptor();
        ToolSpec spec = new ToolSpec(descriptor.name(), descriptor.description(), descriptor.domain(),
                descriptor.readOnly(), descriptor.inputSchema(), descriptor.outputSchema());
        gateway.registerTool(spec, this::reportOutcome);
    }

    private ToolOutcome reportOutcome(Map<String, Object> arguments, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        if (context == null || !context.allows(REPORT))
            return ToolOutcome.failed(requestId, REPORT,
                    new io.devicetruth.core.capability.CapabilityTypes.ToolError(
                            "TOOL_NOT_ALLOWED", "Tool outside server-authorized scope", false));
        try {
            ServerIdentity identity = new ServerIdentity(context.ownerId(), context.projectId());
            RunView run = runs.require(identity, context.runId());
            List<Reference> references = runs.references(identity, context.runId());
            List<AgentRunRepository.ToolEvent> toolEvents = runs.toolEvents(identity, context.runId());
            AgentRunRepository.RunRecord record = runs.load(identity, context.runId(), false);
            List<MessageView> messages = conversations.runMessages(identity, context.runId(), run.goalMessageId());

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("runId", run.runId().toString());
            report.put("conversationId", run.conversationId().toString());
            report.put("status", run.status());
            report.put("dataNature", run.dataNature());
            report.put("stepsUsed", record.stepsUsed());
            report.put("maxSteps", record.maxSteps());
            report.put("attempts", record.attempts());
            report.put("userMessages", messages.stream().filter(message -> "USER".equals(message.role())).count());
            report.put("assistantMessages", messages.stream().filter(message -> "ASSISTANT".equals(message.role())).count());
            if (run.pendingQuestion() != null) {
                Map<String, Object> question = new LinkedHashMap<>();
                question.put("pendingQuestionId", run.pendingQuestion().pendingQuestionId().toString());
                question.put("questionVersion", run.pendingQuestion().questionVersion());
                question.put("question", run.pendingQuestion().question());
                question.put("requestedFields", run.pendingQuestion().requestedFields());
                report.put("pendingQuestion", question);
            }
            if (run.result() != null) {
                Map<String, Object> answer = new LinkedHashMap<>();
                answer.put("summary", run.result().summary());
                answer.put("category", run.result().category().name());
                answer.put("riskLevel", run.result().riskLevel().name());
                answer.put("safetyDisposition", run.result().safetyDisposition());
                answer.put("referenceCount", run.result().references().size());
                report.put("answer", answer);
            }
            if (run.failureCode() != null) report.put("failureCode", run.failureCode());
            report.put("references", references.stream().map(AgentReportTools::referenceView).toList());
            report.put("toolEvents", toolEvents.stream().map(AgentReportTools::toolEventView).toList());
            return ToolOutcome.ok(requestId, REPORT, report, references, dataNature(references));
        } catch (RuntimeException e) {
            return ToolOutcome.failed(requestId, REPORT,
                    new io.devicetruth.core.capability.CapabilityTypes.ToolError(
                            "REPORT_UNAVAILABLE", "Cannot build report for this run", false));
        }
    }

    private static Map<String, Object> referenceView(Reference reference) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("referenceId", reference.referenceId());
        view.put("sourceType", reference.sourceType());
        view.put("sourceId", reference.sourceId());
        view.put("sourceVersion", reference.sourceVersion());
        view.put("title", reference.title());
        view.put("section", reference.section());
        view.put("page", reference.page());
        view.put("dataNature", reference.dataNature());
        view.put("excerpt", truncate(reference.excerpt(), 200));
        return view;
    }

    private static Map<String, Object> toolEventView(AgentRunRepository.ToolEvent event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sequence", event.sequence());
        view.put("toolName", event.toolName());
        view.put("success", event.success());
        view.put("errorCode", event.errorCode());
        view.put("referenceCount", event.referenceCount());
        return view;
    }

    /** 任一引用为模拟数据时报告整体保持 SIMULATED，与知识工具规则一致。 */
    private static String dataNature(List<Reference> references) {
        return references.stream().allMatch(reference -> "AUTHORIZED".equals(reference.dataNature()))
                ? "AUTHORIZED" : "SIMULATED";
    }

    private static String truncate(String content, int max) {
        if (content == null) return "";
        return content.length() <= max ? content : content.substring(0, max);
    }

    public ToolDescriptor descriptor() {
        return new ToolDescriptor(REPORT,
                "生成当前调查的只读报告：运行状态、已取回引用、工具轨迹与预算使用；"
                        + "恢复/刷新后可重新生成，不修改任何状态，不包含推理输入或内部配置",
                "report", true,
                Map.of("type", "object", "additionalProperties", false, "properties", Map.of(), "required", List.of()),
                "Report{runId,conversationId,status,dataNature,stepsUsed,maxSteps,attempts,"
                        + "userMessages,assistantMessages,pendingQuestion?,answer?,failureCode?,"
                        + "references[{referenceId,sourceType,sourceId,sourceVersion,title,section,page,dataNature,excerpt}],"
                        + "toolEvents[{sequence,toolName,success,errorCode,referenceCount}]}");
    }
}
