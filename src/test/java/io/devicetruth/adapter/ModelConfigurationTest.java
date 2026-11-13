package io.devicetruth.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ModelConfigurationTest {
    @Test void embeddingSettingsUseSamePrivateFileAndBlankValuesFallBack(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path = directory.resolve("model.properties");
        java.nio.file.Files.writeString(path, "DEVICETRUTH_EMBEDDING_MODEL=local-model\nDEVICETRUTH_EMBEDDING_API_KEY=\n");
        var settings = new ModelConfiguration(path, java.util.Map.of("DEVICETRUTH_EMBEDDING_API_KEY", "environment-key")::get);
        assertThat(settings.apply("DEVICETRUTH_EMBEDDING_MODEL")).isEqualTo("local-model");
        assertThat(settings.apply("DEVICETRUTH_EMBEDDING_API_KEY")).isEqualTo("environment-key");
    }
    @Test void absentFileKeepsEnvironmentSupport(@TempDir Path directory) {
        var settings=new ModelConfiguration(directory.resolve("missing.properties"),Map.of("OPENAI_API_KEY","environment-key")::get);
        assertThat(settings.apply("OPENAI_API_KEY")).isEqualTo("environment-key");
        assertThat(settings.apply("OPENAI_MODEL")).isNull();
    }

    @Test void fileOverridesEnvironmentAndBlankValuesFallBack(@TempDir Path directory) throws Exception {
        Path path=directory.resolve("private.properties");
        Files.writeString(path,"""
                # UTF-8 本地配置
                OPENAI_API_KEY=file-key=with-equals
                OPENAI_BASE_URL=https://example.com/v1
                OPENAI_MODEL=local-model
                DASHSCOPE_API_KEY=  
                """);
        var settings=new ModelConfiguration(path,Map.of("OPENAI_API_KEY","environment-key","DASHSCOPE_API_KEY"," qwen-env-key ")::get);
        assertThat(settings.apply("OPENAI_API_KEY")).isEqualTo("file-key=with-equals");
        assertThat(settings.apply("OPENAI_BASE_URL")).isEqualTo("https://example.com/v1");
        assertThat(settings.apply("OPENAI_MODEL")).isEqualTo("local-model");
        assertThat(settings.apply("DASHSCOPE_API_KEY")).isEqualTo("qwen-env-key");
    }

    @Test void unreadableOrMalformedFilesFailWithoutExposingContents(@TempDir Path directory) throws Exception {
        var unreadable=new ModelConfiguration(directory,name->null);
        assertThatThrownBy(()->unreadable.apply("OPENAI_API_KEY")).hasMessage("MODEL_CONFIGURATION_UNREADABLE");
        Path path=directory.resolve("invalid.properties");
        Files.writeString(path,"OPENAI_API_KEY="+'\\'+"uZZZZ-private-secret");
        var malformed=new ModelConfiguration(path,name->null);
        assertThatThrownBy(()->malformed.apply("OPENAI_API_KEY")).hasMessage("MODEL_CONFIGURATION_UNREADABLE");
    }
}
