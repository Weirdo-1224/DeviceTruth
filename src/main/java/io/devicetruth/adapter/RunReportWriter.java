package io.devicetruth.adapter;

import io.devicetruth.core.runtime.TaskOrchestrator.RunReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 运行报告写出器：每次运行生成一个 UUID 目录，包含结果、最终状态、证据与事件流四个文件。 */
public final class RunReportWriter {
    /** 写入报告；返回本次运行的目录路径。目录结构：outputs/runs/<runId>/{result,final_state,evidence}.json + events.jsonl */
    public Path write(Path outputRoot,RunReport report) throws IOException {
        Path directory=outputRoot.resolve(UUID.randomUUID().toString()); Files.createDirectories(directory);
        var pretty=Json.MAPPER.writerWithDefaultPrettyPrinter();
        pretty.writeValue(directory.resolve("result.json").toFile(),report.result());
        pretty.writeValue(directory.resolve("final_state.json").toFile(),report.state());
        pretty.writeValue(directory.resolve("evidence.json").toFile(),report.evidence());
        try(var out=Files.newBufferedWriter(directory.resolve("events.jsonl"),StandardCharsets.UTF_8)) {
            for(var event:report.events()) { out.write(Json.write(event)); out.newLine(); }
        }
        return directory;
    }
}
