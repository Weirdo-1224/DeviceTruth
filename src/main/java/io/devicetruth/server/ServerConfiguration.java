package io.devicetruth.server;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.application.ConversationFlowService;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.DeterministicTaskRouter;
import io.devicetruth.application.DiagnosisApplicationService;
import io.devicetruth.application.LlmTaskRouter;
import io.devicetruth.application.QueryService;
import io.devicetruth.application.RouteValidator;
import io.devicetruth.application.SupportedScenarioCatalog;
import io.devicetruth.application.TaskRouter;
import java.io.IOException;
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

    @Bean ConversationFlowService conversationFlowService(ConversationService conversations, TaskRouter router,
            SupportedScenarioCatalog catalog, QueryService queries, DiagnosisApplicationService diagnoses) {
        return new ConversationFlowService(conversations, router, catalog, queries, diagnoses);
    }
}