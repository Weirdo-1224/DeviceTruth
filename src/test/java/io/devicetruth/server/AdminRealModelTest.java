package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.admin.application.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.application.agent.*;
import io.devicetruth.knowledge.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

/** 用户明确授权的单条真实模型烟测；独立于暂停的 Holdout/批量实验。 */
class AdminRealModelTest extends AccountWorkspaceMigrationTest {
    @Test void managedCorpusSkillAndRealModelRunThenDisableRestore() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.admin-real"));
        Path output=Path.of("outputs/validation/platform-admin/real");Files.createDirectories(output);
        var report=new LinkedHashMap<String,Object>();report.put("holdoutExecuted",false);report.put("fieldData",false);
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=true","--devicetruth.agent.reasoner=openai","--devicetruth.files.root="+output.resolve("files").toAbsolutePath())) {
            var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var admin=app.getBean(AccountService.class).register("admin-real@example.com","isolated-real-password-123","运营验收");
            var maintenance=new AdminMaintenance(jdbc,tx);maintenance.platform("isolated-real-test",admin.userId(),true,true,"OPS_REQUEST");
            var service=app.getBean(AdminService.class);var created=service.createUser(admin.userId(),UUID.randomUUID(),"member-real@example.com","设备检修员","isolated-real-member-password-123");
            UUID actor=(UUID)created.get("userId"),workspace=(UUID)created.get("personalWorkspaceId");
            maintenance.grant("isolated-real-test",admin.userId(),workspace,"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");maintenance.grant("isolated-real-test",admin.userId(),workspace,"SKILL_MANAGE",true,"OPS_REQUEST");
            var known=KnowledgeCorpus.load(Path.of("datasets/knowledge")).stream().filter(d->d.knowledgeId().equals("EV-0001")).findFirst().orElseThrow();
            report.put("knowledgeId",known.knowledgeId());report.put("sourceNature",known.sourceNature());report.put("accessBasis",KnowledgeCorpus.AUTHORIZATION_BASIS);
            var accepted=app.getBean(AdminKnowledgeOperations.class).upload(admin.userId(),UUID.randomUUID(),workspace,null,new MockMultipartFile("file","known.md","text/markdown",known.originalText().getBytes(StandardCharsets.UTF_8)),Map.of("title",known.title(),"version","real-admin-v1","sourceId",known.sourceIds().getFirst(),"licenseCode",KnowledgeCorpus.AUTHORIZATION_BASIS));
            UUID file=(UUID)accepted.get("fileId");var views=new AdminKnowledgeViews(service,app.getBean(AdminPages.class));Map<String,Object> state=null;
            for(int i=0;i<360;i++){state=views.file(admin.userId(),workspace,null,file);if(!Set.of("UPLOADED","PROCESSING").contains(state.get("processingStatus")))break;Thread.sleep(250);}
            report.put("importStatus",state.get("processingStatus"));report.put("importErrorCode",state.get("errorCode"));assertThat(state.get("processingStatus")).isEqualTo("PARSED");
            var runtime=app.getBean(io.devicetruth.skill.SkillRuntime.class);var skill=runtime.registry().require("automotive-diagnosis");service.install(admin.userId(),UUID.randomUUID(),workspace,null,skill.name(),skill.version(),"OPS_REQUEST");
            var scope=new WorkspaceScopeResolver(jdbc).resolve(actor,workspace);var agent=app.getBean(GeneralAgentService.class).forScope(scope);var conversation=agent.create();
            var run=agent.submit(conversation.conversationId(),UUID.randomUUID(),new GeneralAgentService.AgentTaskInput(1,"这是已授权本地研究资料的知识说明验收，不是现场故障确诊。请调用 search_knowledge 检索本空间标题为“"+known.title()+"”的资料，引用已取得的原文概括一条重要检修检查信息。不需要追问；没有现场设备、故障码或实测，不得捏造现场状态。",null,null,null,null,null));
            GeneralAgentService.RunView result=await(agent,run.runId());
            if(result.status().equals("WAITING_FOR_USER")){var question=result.pendingQuestion();agent.submit(conversation.conversationId(),UUID.randomUUID(),new GeneralAgentService.AgentTaskInput(1,"只需要检索并引用现有手册做知识说明，不做现场诊断。",null,null,null,null,Map.of("runId",run.runId().toString(),"pendingQuestionId",question.pendingQuestionId().toString(),"questionVersion",question.questionVersion())));result=await(agent,run.runId());}
            var models=agent.modelEvents(run.runId());var refs=agent.references(run.runId());report.put("runId",run.runId());report.put("status",result.status());report.put("failureCode",result.failureCode());report.put("modelAttempts",models.size());report.put("models",models.stream().map(m->m.model()).distinct().toList());report.put("referenceCount",refs.size());report.put("quotaEnforcement","UNCONFIGURED");
            var embedding=app.getBean(EmbeddingClient.class);report.put("embeddingProvider",embedding.profile().provider());report.put("embeddingModel",embedding.profile().model());if(embedding instanceof OpenAiCompatibleEmbeddingClient online)report.put("embeddingUsage",online.usage());
            assertThat(models).isNotEmpty();assertThat(result.status()).isEqualTo("COMPLETED");assertThat(refs).isNotEmpty();
            var summary=new AdminReadModels(service,app.getBean(AdminPages.class)).run(admin.userId(),run.runId());assertThat(Json.write(summary)).doesNotContain(known.originalText());
            service.userStatus(admin.userId(),UUID.randomUUID(),actor,"DISABLED",0,"OPS_REQUEST");assertThatThrownBy(()->new WorkspaceScopeResolver(jdbc).resolve(actor,workspace)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            service.userStatus(admin.userId(),UUID.randomUUID(),actor,"ACTIVE",1,"ACCOUNT_RECOVERY");assertThat(agent.run(run.runId()).status()).isEqualTo("COMPLETED");report.put("disableRestoreVerified",true);
        } finally {Files.writeString(output.resolve("report.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));}
    }
    private GeneralAgentService.RunView await(GeneralAgentService service,UUID run) throws Exception {for(int i=0;i<600;i++){var value=service.run(run);if(!Set.of("QUEUED","RUNNING").contains(value.status()))return value;Thread.sleep(250);}throw new AssertionError("Real model run timed out");}
}
