package io.devicetruth.app;

import io.devicetruth.adapter.Json;
import io.devicetruth.application.maintenance.LegacyOwnershipClaimService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class LegacyOwnershipClaimTool {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyOwnershipClaimTool.class);
    private LegacyOwnershipClaimTool() {}

    public static void main(String[] args) {
        int exit = execute(args, System.getenv());
        if (exit != 0) System.exit(exit);
    }

    public static int execute(String[] args, Map<String, String> environment) {
        try {
            Map<String, String> options = options(args);
            if (options.containsKey("help")) {
                LOG.info("dry-run 默认只读。必需：--owner-id= --project-id= --admin-user-id= --team-name= --files-root= --output=；写入另加 --execute。数据库使用 SPRING_DATASOURCE_URL/USERNAME/PASSWORD。");
                return 0;
            }
            DriverManager.setLoginTimeout(5);
            var source = new DriverManagerDataSource(required(environment, "SPRING_DATASOURCE_URL"),
                    required(environment, "SPRING_DATASOURCE_USERNAME"), required(environment, "SPRING_DATASOURCE_PASSWORD"));
            var request = new LegacyOwnershipClaimService.Request(required(options, "owner-id"),
                    required(options, "project-id"), UUID.fromString(required(options, "admin-user-id")),
                    required(options, "team-name"), Path.of(required(options, "files-root")));
            var service = new LegacyOwnershipClaimService(source);
            boolean execute = options.containsKey("execute");
            var manifest = execute ? service.claim(request) : service.dryRun(request);
            Path output = Path.of(required(options, "output")).toAbsolutePath();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), manifest);
            LOG.info("legacy ownership claim mode={} valid={} workspaceId={} manifest={}",
                    manifest.mode(), manifest.valid(), manifest.workspaceId(), output);
            return manifest.valid() ? 0 : 2;
        } catch (Exception e) {
            LOG.error("Legacy ownership claim failed: {}", e.getMessage());
            return 2;
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) throw new IllegalArgumentException("Invalid argument");
            String key;
            String value = "";
            int split = arg.indexOf('=');
            if (split >= 0) {
                key = arg.substring(2, split);
                value = arg.substring(split + 1);
            } else key = arg.substring(2);
            if (!key.matches("help|execute|owner-id|project-id|admin-user-id|team-name|files-root|output")
                    || result.put(key, value) != null) throw new IllegalArgumentException("Invalid or duplicate argument: " + key);
        }
        return result;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required value: " + key);
        return value;
    }
}
