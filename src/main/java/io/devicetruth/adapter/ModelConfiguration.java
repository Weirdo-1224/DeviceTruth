package io.devicetruth.adapter;

import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

/** 首次推理时加载工作目录中的私有配置；文件非空值优先，环境变量补缺。 */
final class ModelConfiguration implements Function<String,String> {
    private final Path path;
    private final Function<String,String> environment;
    private Properties properties;

    static ModelConfiguration local() {
        return new ModelConfiguration(Path.of("devicetruth.local.properties"),System::getenv);
    }

    ModelConfiguration(Path path,Function<String,String> environment) {
        this.path=path;this.environment=environment;
    }

    @Override public String apply(String name) {
        if(properties==null) {
            var loaded=new Properties();
            try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { loaded.load(reader); }
            catch(NoSuchFileException e) { /* 文件可选，继续使用环境变量。 */ }
            catch(IOException|IllegalArgumentException|SecurityException e) {
                throw new ReasonerFailure("MODEL_CONFIGURATION_UNREADABLE");
            }
            properties=loaded;
        }
        String value=properties.getProperty(name);
        if(value==null||value.isBlank()) value=environment.apply(name);
        return value==null?null:value.trim();
    }
}
