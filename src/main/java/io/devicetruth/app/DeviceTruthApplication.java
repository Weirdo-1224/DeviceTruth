package io.devicetruth.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.WebApplicationType;
import io.devicetruth.adapter.*;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.reasoning.ScriptedReasoner;
import java.nio.file.Path;

/** DeviceTruth Core CLI 入口：解析参数后执行一次（全模拟数据的）诊断运行，并把报告写入 outputs/runs。 */
@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.session.SessionAutoConfiguration.class,
        org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class})
public class DeviceTruthApplication {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceTruthApplication.class);

    public static void main(String[] args) {
        int exit=execute(args);
        if(exit!=0) System.exit(exit);
    }

    /** 返回进程退出码：0 成功；2 参数错误、运行失败或技术性失败。供 shell 脚本与 CLI 验证程序断言。 */
    public static int execute(String[] args) {
        CliOptions options;
        try { options=CliOptions.parse(args); }
        catch(IllegalArgumentException e) { LOG.error("{}; {}",e.getMessage(),help()); return 2; }
        if(options.help()) { LOG.info("{}",help()); return 0; }
        // 以非 Web 模式启动 Spring，仅借用其装配能力；关闭 banner 与调试日志保持 CLI 输出干净
        SpringApplication app = new SpringApplication(DeviceTruthApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        try (ConfigurableApplicationContext context = app.run("--debug=false", "--trace=false", "--logging.level.org.springframework=INFO")) {
            // 1) 读取场景定义与打包数据；2) 装配能力网关与推理器；3) 运行；4) 报告落盘
            var scenarios=JsonRepositories.readResource("datasets/scenarios.json",com.fasterxml.jackson.databind.JsonNode.class);
            DiagnosisRequest request=Json.MAPPER.treeToValue(scenarios.get(options.scenario()),DiagnosisRequest.class);
            var repository=JsonRepositories.fromClasspath();
            var gateway=CapabilityGateway.standard(repository,repository,repository);
            Reasoner reasoner=switch(options.reasoner()) {
                case "qwen" -> new QwenReasoner();
                case "openai" -> new OpenAiReasoner();
                default -> new ScriptedReasoner();
            };
            var report=new TaskOrchestrator(gateway,new EvidenceManager(),new DecisionGuard()).run(request,options.budget(),reasoner);
            Path output=new RunReportWriter().write(Path.of("outputs/runs"),report);
            LOG.info("reasoner={} data=SIMULATED runStatus={} outcome={} steps={} report={}",reasoner.name(),
                    report.result().runStatus(),report.result().outcome(),report.result().stepsUsed(),output.toAbsolutePath());
            if(report.result().runStatus()==RunStatus.FAILED) {
                LOG.error("Technical failure: {}. Check devicetruth.local.properties or environment variables in README.",report.result().stopReason());
                return 2;
            }
            return 0;
        } catch(Exception e) {
            LOG.error("Run failed: {}. See README and verify packaged data and output directory.",e.getClass().getSimpleName());
            return 2;
        }
    }

    public static String help() {
        return "DeviceTruth Core 0.1 (SIMULATED) --help --scenario=A|B|C|D --reasoner=scripted|qwen|openai --max-steps=12 --max-repeated-actions=2; reports: outputs/runs/<runId>/";
    }
}
