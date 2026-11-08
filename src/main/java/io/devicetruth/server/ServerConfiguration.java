package io.devicetruth.server;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.adapter.OpenAiReasoner;
import io.devicetruth.adapter.QwenReasoner;
import io.devicetruth.adapter.RunReportWriter;
import io.devicetruth.application.ConversationFlowService;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.DeterministicTaskRouter;
import io.devicetruth.application.DiagnosisApplicationService;
import io.devicetruth.application.DiagnosisRunService;
import io.devicetruth.application.LlmTaskRouter;
import io.devicetruth.application.QueryService;
import io.devicetruth.application.ReasonerFactory;
import io.devicetruth.application.RouteValidator;
import io.devicetruth.application.SupportedScenarioCatalog;
import io.devicetruth.application.TaskRouter;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.Budget;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.reasoning.ScriptedReasoner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Server 侧集中装配：只扫描新增 api 包，core/adapter/app 不新增 Spring 组件。 */
@Configuration
@ComponentScan(basePackages = "io.devicetruth.api")
public class ServerConfiguration {
    @Bean JsonRepositories jsonRepositories() throws IOException { return JsonRepositories.fromClasspath(); }

    @Bean SupportedScenarioCatalog supportedScenarioCatalog() throws IOException {
        return SupportedScenarioCatalog.fromClasspath();
    }

    @Bean ConversationService conversationService(
            @Value("${devicetruth.session.max-sessions:100}") int maxSessions,
            @Value("${devicetruth.session.max-messages-per-session:40}") int maxMessagesPerSession) {
        return new ConversationService(maxSessions, maxMessagesPerSession);
    }

    @Bean RouteValidator routeValidator(SupportedScenarioCatalog catalog, JsonRepositories repositories) {
        return new RouteValidator(catalog, repositories, repositories, repositories);
    }

    @Bean TaskRouter taskRouter(RouteValidator validator,
            @Value("${devicetruth.router.mode:offline}") String mode,
            @Value("${devicetruth.core.reasoner:scripted}") String reasoner) {
        if ("offline".equalsIgnoreCase(mode)) return new DeterministicTaskRouter(validator);
        if ("online".equalsIgnoreCase(mode)) return new LlmTaskRouter(new OnlineRouterCompletion(reasoner), validator);
        throw new IllegalArgumentException("devicetruth.router.mode must be offline or online");
    }

    /** 事实查询只依赖只读 AssetRepository；不构造诊断 Scope，也不经过 CapabilityGateway。 */
    @Bean QueryService queryService(JsonRepositories repositories) {
        return new QueryService(repositories);
    }

    @Bean DiagnosisApplicationService diagnosisApplicationService(SupportedScenarioCatalog catalog,
            RouteValidator validator) {
        return new DiagnosisApplicationService(catalog, validator);
    }

    /** 只读仓储与注册完成后不再修改的 Gateway 可以共享；Reasoner 绝不能注册成 Bean。 */
    @Bean CapabilityGateway capabilityGateway(JsonRepositories repositories) {
        return CapabilityGateway.standard(repositories, repositories, repositories);
    }

    /**
     * 只读仓储与注册完成后不再修改的 Gateway 可以共享；Reasoner 绝不能注册成 Bean，
     * 否则多个并发运行会共用可变的 attempts / 延迟初始化连接，这里只提供每任务新建工厂。
     * 未知的 reasoner 名称不让容器启动失败：诊断提交时 `create()` 抛 InitException，
     * 由应用层映射为稳定错误码，健康端点在模型配置错误时仍可用。
     */
    @Bean ReasonerFactory reasonerFactory(@Value("${devicetruth.core.reasoner:scripted}") String reasoner) {
        String name = reasoner == null ? "" : reasoner.trim().toLowerCase();
        Supplier<Reasoner> creator = switch (name) {
            case "qwen" -> QwenReasoner::new;
            case "openai" -> OpenAiReasoner::new;
            case "scripted" -> ScriptedReasoner::new;
            default -> () -> { throw new InitException("UNSUPPORTED_REASONER_CONFIGURATION"); };
        };
        return ReasonerFactory.of(name, creator);
    }

    /** 推理器构造阶段的稳定失败码（不携带配置值、路径或异常正文）。 */
    public static final class InitException extends RuntimeException {
        private final String code;
        public InitException(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }

    @Bean DiagnosisRunService diagnosisRunService(ConversationService conversations, SupportedScenarioCatalog catalog,
            CapabilityGateway gateway, ReasonerFactory reasonerFactory,
            @Value("${devicetruth.run.output-root:outputs/runs}") String outputRoot,
            @Value("${devicetruth.run.max-concurrent-runs:2}") int maxConcurrentRuns,
            @Value("${devicetruth.run.max-queue:10}") int maxQueue,
            @Value("${devicetruth.run.max-stored-runs:100}") int maxStoredRuns) {
        return new DiagnosisRunService(conversations, catalog, gateway, reasonerFactory, Budget.defaults(),
                new RunReportWriter(), Path.of(outputRoot), maxConcurrentRuns, maxQueue, maxStoredRuns);
    }

    @Bean ConversationFlowService conversationFlowService(ConversationService conversations, TaskRouter router,
            SupportedScenarioCatalog catalog, QueryService queries, DiagnosisApplicationService diagnoses,
            DiagnosisRunService runs) {
        return new ConversationFlowService(conversations, router, catalog, queries, diagnoses, runs);
    }
}