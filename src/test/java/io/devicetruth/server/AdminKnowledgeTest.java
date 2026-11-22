package io.devicetruth.server;

import io.devicetruth.admin.application.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.knowledge.*;
import io.devicetruth.application.agent.*;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class AdminKnowledgeTest extends AccountWorkspaceMigrationTest {
    @Test void existingCorpusImportsAreVisibleWithoutFabricatingAdminJobsOrExposingAttachments() throws Exception {
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic")) {
            var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"corpus-admin@example.com","isolated-password-123","运营");var member=accounts.register("corpus-member@example.com","isolated-password-123","检修员");
            var scope=new WorkspaceScopeResolver(jdbc).resolve(member.userId(),null);UUID workspace=scope.workspaceId();var maintenance=new AdminMaintenance(jdbc,tx);
            maintenance.platform("test",admin.userId(),true,true,"OPS_REQUEST");maintenance.grant("test",admin.userId(),workspace,"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");
            var embeddings=app.getBean(EmbeddingClient.class);
            var plan=KnowledgeCorpusService.prepare(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),"existing-corpus",new KnowledgeIndexProfile("E3",512,KnowledgeIndexProfile.Context.METADATA),embeddings.profile().profileVersion(),KnowledgeCorpus.load(Path.of("datasets/knowledge")).subList(0,3));
            var corpus=new KnowledgeCorpusService(jdbc,tx,embeddings,true);corpus.stagePlan(plan);corpus.markReady(plan);
            var views=new AdminKnowledgeViews(app.getBean(AdminService.class),app.getBean(AdminPages.class));
            var first=views.emptyManaged(admin.userId(),workspace,null,Map.of("workspaceId",workspace.toString(),"limit","1"),"files");
            assertThat(first.items()).hasSize(1);assertThat(first.nextCursor()).isNotNull();
            var item=first.items().getFirst();assertThat(item).containsEntry("importOrigin","CORPUS_IMPORT").containsEntry("processingStatus","PARSED").containsEntry("retryable",false);
            var second=views.emptyManaged(admin.userId(),workspace,null,Map.of("workspaceId",workspace.toString(),"limit","1","cursor",first.nextCursor()),"files");
            assertThat(second.items()).hasSize(1);assertThat(second.items().getFirst().get("fileId")).isNotEqualTo(item.get("fileId"));
            UUID document=(UUID)item.get("documentId"),file=(UUID)item.get("fileId");
            assertThat(views.document(admin.userId(),workspace,null,document).get("file")).isEqualTo(views.file(admin.userId(),workspace,null,file));
            assertThat(views.file(admin.userId(),workspace,null,file)).doesNotContainKeys("body","originalText","pathKey","actorUserId");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_admin_import",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT active FROM dt_knowledge_release WHERE id=?",Boolean.class,plan.releaseId())).isFalse();
            assertThatThrownBy(()->app.getBean(AdminKnowledgeOperations.class).retry(admin.userId(),UUID.randomUUID(),workspace,null,file,0,"OPS_REQUEST")).hasMessage("RESOURCE_NOT_FOUND");
            var other=new WorkspaceScopeResolver(jdbc).resolve(admin.userId(),null).workspaceId();
            maintenance.grant("test",admin.userId(),other,"KNOWLEDGE_READ",true,"OPS_REQUEST");
            assertThat(views.emptyManaged(admin.userId(),other,null,Map.of("workspaceId",other.toString()),"files").items()).isEmpty();
            assertThatThrownBy(()->views.file(admin.userId(),other,null,file)).hasMessage("RESOURCE_NOT_FOUND");
            var conversation=app.getBean(GeneralAgentService.class).forScope(scope).create();
            jdbc.update("UPDATE dt_uploaded_file SET usage_scope='CONVERSATION_ATTACHMENT',conversation_id=? WHERE id=?",conversation.conversationId(),file);
            assertThat(views.emptyManaged(admin.userId(),workspace,null,Map.of("workspaceId",workspace.toString()),"files").items()).hasSize(2);
            assertThat(views.documents(admin.userId(),workspace,null,Map.of("workspaceId",workspace.toString())).items()).hasSize(2);
            assertThatThrownBy(()->views.file(admin.userId(),workspace,null,file)).hasMessage("RESOURCE_NOT_FOUND");
        }
    }

    @Test void knownCorpusImportDisableHistoryRestoreAndDuplicateReuseKeepTheirBoundaries() throws Exception {
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic")) {
            var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"knowledge-admin@example.com","isolated-password-123","运营");var member=accounts.register("knowledge-member@example.com","isolated-password-123","检修员");
            var scope=new WorkspaceScopeResolver(jdbc).resolve(member.userId(),null);UUID workspace=scope.workspaceId();var maintenance=new AdminMaintenance(jdbc,tx);
            maintenance.platform("test",admin.userId(),true,true,"OPS_REQUEST");maintenance.grant("test",admin.userId(),workspace,"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");
            var service=app.getBean(AdminService.class);var ops=app.getBean(AdminKnowledgeOperations.class);var pages=app.getBean(AdminPages.class);var views=new AdminKnowledgeViews(service,pages);
            var known=KnowledgeCorpus.load(Path.of("datasets/knowledge")).getFirst();var file=new MockMultipartFile("file","known.md","text/markdown",known.originalText().getBytes(StandardCharsets.UTF_8));
            var fields=Map.of("title",known.title(),"sourceId",known.sourceIds().getFirst(),"licenseCode",KnowledgeCorpus.AUTHORIZATION_BASIS,"version","admin-test-v1");
            var accepted=ops.upload(admin.userId(),UUID.randomUUID(),workspace,null,file,fields);UUID fileId=(UUID)accepted.get("fileId");
            Map<String,Object> state=null;
            for(int i=0;i<200;i++){state=views.file(admin.userId(),workspace,null,fileId);if(!Set.of("UPLOADED","PROCESSING").contains(state.get("processingStatus")))break;Thread.sleep(25);}
            assertThat(state.get("processingStatus")).isEqualTo("PARSED");UUID document=(UUID)state.get("documentId");
            var detail=views.document(admin.userId(),workspace,null,document);assertThat(detail).doesNotContainKeys("body","originalText");assertThat(detail.get("sectionCount")).isNotNull();
            var agent=app.getBean(GeneralAgentService.class).forScope(scope);var conversation=agent.create();var run=agent.submit(conversation.conversationId(),UUID.randomUUID(),new GeneralAgentService.AgentTaskInput(1,"模拟知识核验",null,null,null,null,null));
            var context=new io.devicetruth.core.capability.ToolContext(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),conversation.conversationId(),run.runId(),null,java.time.Instant.now(),java.time.Instant.now(),Set.of("search_knowledge","get_document_excerpt"),member.userId(),workspace);
            var searches=app.getBean(KnowledgeSearchService.class);
            var filters=new KnowledgeSearchService.KnowledgeFilters(null,null,document);
            var first=searches.searchChannel(context,known.title(),filters,5,KnowledgeSearchService.SearchChannel.HYBRID);assertThat(first.hits()).isNotEmpty();
            UUID reference=first.hits().getFirst().referenceId();
            ops.status(admin.userId(),UUID.randomUUID(),workspace,null,document,"DISABLED",0,"DATA_OUTDATED");
            for(var channel:KnowledgeSearchService.SearchChannel.values())assertThat(searches.searchChannel(context,known.title(),filters,5,channel).hits()).as(channel.name()).isEmpty();
            var repository=new AgentRunRepository(jdbc);assertThat(repository.references(scope.storageIdentity(),run.runId())).isNotEmpty();
            // 首轮 scripted 调查停在等待补答；按“一轮一活动”合同先取消它，才能为同一会话开启新一轮。
            agent.cancel(run.runId());
            for(int i=0;i<200;i++){var current=jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?",String.class,run.runId());
                if("CANCELLED".equals(current))break;Thread.sleep(25);}
            var next=agent.submit(conversation.conversationId(),UUID.randomUUID(),new GeneralAgentService.AgentTaskInput(1,"新一轮",null,null,null,null,null));
            assertThatThrownBy(()->tx.execute(t->repository.importReference(scope.storageIdentity(),reference,next.runId()))).isInstanceOf(GeneralAgentService.RequestFailure.class);
            assertThatThrownBy(()->app.getBean(FileUploadService.class).readyAttachments(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),List.of(Map.of("fileId",fileId.toString())))).isInstanceOf(GeneralAgentService.RequestFailure.class);
            var reversion=app.getBean(KnowledgeImportService.class).importDocument(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),new KnowledgeImportService.ImportRequest(known.title(),known.domain(),null,"another-version","known-version.md","text/markdown",known.originalText(),"AUTHORIZED"));
            assertThat(jdbc.queryForObject("SELECT availability FROM dt_knowledge_document WHERE id=?",String.class,reversion.document().documentId())).isEqualTo("DISABLED");
            // 模拟进程在 PROCESSING 崩溃：过期导入租约可恢复，复用不得重置停用。
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='PROCESSING' WHERE id=?",fileId);
            jdbc.update("UPDATE dt_admin_import SET import_lease_owner='crashed-worker',import_lease_until=clock_timestamp()-interval '1 second' WHERE file_id=?",fileId);
            for(int i=0;i<200;i++){app.getBean(FileUploadService.class).recoverManagedImports();if(views.file(admin.userId(),workspace,null,fileId).get("processingStatus").equals("PARSED"))break;Thread.sleep(25);}
            assertThat(views.file(admin.userId(),workspace,null,fileId).get("processingStatus")).isEqualTo("PARSED");
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='FAILED',parse_error='IMPORT_FAILED' WHERE id=?",fileId);
            ops.retry(admin.userId(),UUID.randomUUID(),workspace,null,fileId,0,"OPS_REQUEST");
            assertThatThrownBy(()->ops.retry(admin.userId(),UUID.randomUUID(),workspace,null,fileId,0,"OPS_REQUEST")).hasMessage("STATE_VERSION_CONFLICT");
            for(int i=0;i<200;i++){if(views.file(admin.userId(),workspace,null,fileId).get("processingStatus").equals("PARSED"))break;Thread.sleep(25);}
            assertThat(views.file(admin.userId(),workspace,null,fileId).get("processingStatus")).isEqualTo("PARSED");
            ops.upload(admin.userId(),UUID.randomUUID(),workspace,null,file,fields);
            assertThat(jdbc.queryForObject("SELECT availability FROM dt_knowledge_document WHERE id=?",String.class,document)).isEqualTo("DISABLED");
            assertThatThrownBy(()->ops.status(admin.userId(),UUID.randomUUID(),workspace,null,document,"ENABLED",0,"OPS_REQUEST")).hasMessage("STATE_VERSION_CONFLICT");
            ops.status(admin.userId(),UUID.randomUUID(),workspace,null,document,"ENABLED",1,"OPS_REQUEST");
            assertThat(searches.searchChannel(context,known.title(),filters,5,KnowledgeSearchService.SearchChannel.HYBRID).hits()).isNotEmpty();
            var forged=new MockMultipartFile("file","unknown.md","text/markdown","# Unknown\nNot in original corpus".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(()->ops.upload(admin.userId(),UUID.randomUUID(),workspace,null,forged,fields)).hasMessage("SOURCE_DOCUMENT_NOT_REGISTERED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE workspace_id=? AND user_id=?",Long.class,workspace,admin.userId())).isZero();
        }
    }
    @Test void normalUploadKeepsActorEpochAcrossDisableAndImmediateRestore() throws Exception {
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic")) {
            var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"upload-admin@example.com","isolated-password-123","运营");var actor=accounts.register("upload-actor@example.com","isolated-password-123","上传者");
            new AdminMaintenance(jdbc,tx).platform("test",admin.userId(),true,true,"OPS_REQUEST");var scope=new WorkspaceScopeResolver(jdbc).resolve(actor.userId(),null);
            app.getBean(FileUploadService.class).close();
            var entered=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);var original=app.getBean(EmbeddingClient.class);
            var blocking=new EmbeddingClient(){public EmbeddingProfile profile(){return original.profile();}public float[] embed(String text){entered.countDown();try{finish.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return original.embed(text);}};
            var imports=new KnowledgeImportService(jdbc,tx,blocking,1000);
            try(var files=new FileUploadService(jdbc,imports,java.nio.file.Files.createTempDirectory("admin-upload-epoch"),1,2)) {
                new AdminKnowledgeOperations(app.getBean(AdminService.class),jdbc,tx,files,imports,Path.of("datasets/knowledge"),app.getBean(AdminPages.class));
                var accepted=files.uploadForActor(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),new MockMultipartFile("file","normal.md","text/markdown","# 模拟材料\n\n记录设备型号和时间。".getBytes(StandardCharsets.UTF_8)),new FileUploadService.ImportMetadata("模拟材料","general",null,"v1","SIMULATED"),actor.userId());
                assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                app.getBean(AdminService.class).userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"DISABLED",0,"OPS_REQUEST");app.getBean(AdminService.class).userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"ACTIVE",1,"ACCOUNT_RECOVERY");finish.countDown();
                FileUploadService.FileView state=null;for(int i=0;i<200;i++){state=files.require(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),accepted.fileId());if(state.parseStatus().equals("FAILED"))break;Thread.sleep(25);}
                assertThat(state.parseStatus()).isEqualTo("FAILED");assertThat(state.failureCode()).isEqualTo("IMPORT_AUTHORIZATION_REVOKED");assertThat(state.documentId()).isNull();
            }finally{finish.countDown();}
        }
    }

    @Test void revokedManagedImportRemainsAddressableWithoutDocumentAndCanRetrySameFile() throws Exception {
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic")) {
            var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var admin=legacyAdminFixture(app.getBean(AccountService.class),"revoked-import@example.com","isolated-password-123","运营");var scope=new WorkspaceScopeResolver(jdbc).resolve(admin.userId(),null);var maintenance=new AdminMaintenance(jdbc,tx);maintenance.platform("test",admin.userId(),true,true,"OPS_REQUEST");maintenance.grant("test",admin.userId(),scope.workspaceId(),"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");
            app.getBean(FileUploadService.class).close();var entered=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);var delegate=app.getBean(EmbeddingClient.class);
            var blocking=new EmbeddingClient(){public EmbeddingProfile profile(){return delegate.profile();}public float[] embed(String text){entered.countDown();try{finish.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return delegate.embed(text);}};
            var imports=new KnowledgeImportService(jdbc,tx,blocking,1000);
            try(var files=new FileUploadService(jdbc,imports,java.nio.file.Files.createTempDirectory("managed-import-revoked"),1,2)) {
                var service=app.getBean(AdminService.class);var pages=app.getBean(AdminPages.class);var ops=new AdminKnowledgeOperations(service,jdbc,tx,files,imports,Path.of("datasets/knowledge"),pages);var views=new AdminKnowledgeViews(service,pages);var known=KnowledgeCorpus.load(Path.of("datasets/knowledge")).getFirst();
                var accepted=ops.upload(admin.userId(),UUID.randomUUID(),scope.workspaceId(),null,new MockMultipartFile("file","known.md","text/markdown",known.originalText().getBytes(StandardCharsets.UTF_8)),Map.of("title",known.title(),"sourceId",known.sourceIds().getFirst(),"licenseCode",KnowledgeCorpus.AUTHORIZATION_BASIS,"version","revoked-v1"));UUID file=(UUID)accepted.get("fileId");assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                maintenance.grant("test",admin.userId(),scope.workspaceId(),"KNOWLEDGE_MANAGE",false,"PERMISSION_CHANGE");finish.countDown();
                for(int i=0;i<200;i++){if(jdbc.queryForObject("SELECT parse_status FROM dt_uploaded_file WHERE id=?",String.class,file).equals("FAILED"))break;Thread.sleep(25);}
                maintenance.grant("test",admin.userId(),scope.workspaceId(),"KNOWLEDGE_MANAGE",true,"PERMISSION_CHANGE");
                var failed=views.file(admin.userId(),scope.workspaceId(),null,file);assertThat(failed.get("processingStatus")).isEqualTo("FAILED");assertThat(failed.get("documentId")).isNull();assertThat(failed.get("retryable")).isEqualTo(true);
                assertThat(views.emptyManaged(admin.userId(),scope.workspaceId(),null,Map.of("workspaceId",scope.workspaceId().toString()),"files").items()).hasSize(1);
                ops.retry(admin.userId(),UUID.randomUUID(),scope.workspaceId(),null,file,0,"OPS_REQUEST");
                for(int i=0;i<200;i++){if(views.file(admin.userId(),scope.workspaceId(),null,file).get("processingStatus").equals("PARSED"))break;Thread.sleep(25);}
                assertThat(views.file(admin.userId(),scope.workspaceId(),null,file).get("processingStatus")).isEqualTo("PARSED");assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file",Long.class)).isEqualTo(1);
            }finally{finish.countDown();}
        }
    }

}
