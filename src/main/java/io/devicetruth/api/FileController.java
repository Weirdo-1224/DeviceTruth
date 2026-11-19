package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.ErrorResponse;
import io.devicetruth.knowledge.FileUploadService;
import io.devicetruth.knowledge.KnowledgeException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/** 受控上传、导入状态与失败重试；owner/project 只取服务器配置。 */
@RestController
@Profile("product")
public class FileController {
    private static final Logger LOG = LoggerFactory.getLogger(FileController.class);
    private final FileUploadService files;
    private final io.devicetruth.server.HttpAccess access;
    private String ownerId() { return access.current().storageIdentity().ownerId(); }
    private String projectId() { return access.current().storageIdentity().projectId(); }

    public FileController(FileUploadService files, io.devicetruth.server.HttpAccess access) {
        this.files = files;
        this.access = access;
    }

    @PostMapping(value = "/api/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FileUploadService.FileView> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title, @RequestParam(defaultValue = "general") String domain,
            @RequestParam(required = false) String deviceModel, @RequestParam(defaultValue = "1.0") String version,
            @RequestParam(defaultValue = "SIMULATED") String dataNature) {
        return ResponseEntity.accepted().body(files.upload(ownerId(), projectId(), file,
                new FileUploadService.ImportMetadata(title, domain, deviceModel, version, dataNature)));
    }

    @GetMapping("/api/files/{fileId}")
    FileUploadService.FileView file(@PathVariable UUID fileId) {
        return files.require(ownerId(), projectId(), fileId);
    }

    @PostMapping("/api/files/{fileId}/retry")
    ResponseEntity<FileUploadService.FileView> retry(@PathVariable UUID fileId) {
        return ResponseEntity.accepted().body(files.retry(ownerId(), projectId(), fileId));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<?> accessFailure(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("code", e.getReason()==null?"NOT_FOUND_OR_DENIED":e.getReason()));
    }

    @ExceptionHandler(KnowledgeException.class)
    ResponseEntity<ErrorResponse> knowledgeFailure(KnowledgeException failure) {
        return error(failure.status(), failure.code());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge() { return error(413, "DOCUMENT_TOO_LARGE"); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalidRequest() { return error(400, "INVALID_REQUEST"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> internalError(Exception exception) {
        LOG.error("File API failed: {}", exception.getClass().getSimpleName());
        return error(500, "INTERNAL_ERROR");
    }

    private static ResponseEntity<ErrorResponse> error(int status, String code) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, "请求无法处理。",
                UUID.randomUUID().toString()));
    }
}
