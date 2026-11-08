package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.ErrorResponse;
import io.devicetruth.application.ApplicationExceptions;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 统一错误体 {code,message,requestId}；message 固定，不泄漏内部细节、路径或凭据。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApplicationExceptions.SessionNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的对象不存在或不属于当前会话。");
    }

    @ExceptionHandler({ApplicationExceptions.SessionCapacityExceededException.class,
            ApplicationExceptions.MessageCapacityExceededException.class})
    ResponseEntity<ErrorResponse> capacity() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "SESSION_CAPACITY_EXCEEDED", "会话或消息容量已达上限，请稍后再试。");
    }

    @ExceptionHandler(ApplicationExceptions.RouterUnavailableException.class)
    ResponseEntity<ErrorResponse> routerUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ROUTER_UNAVAILABLE", "路由模型当前不可用，请检查服务端配置。");
    }

    @ExceptionHandler(ApplicationExceptions.NotImplementedException.class)
    ResponseEntity<ErrorResponse> notImplemented() {
        return error(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "该路由已识别，但对应处理将在后续里程碑启用。");
    }

    @ExceptionHandler(ApplicationExceptions.RunCapacityExceededException.class)
    ResponseEntity<ErrorResponse> runCapacity() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RUN_CAPACITY_EXCEEDED", "诊断执行队列已满，请稍后再试。");
    }

    /** 仓储返回了不属于请求资产的记录：属于内部一致性问题，不对外透传任何记录内容。 */
    @ExceptionHandler(ApplicationExceptions.QueryAssetMismatchException.class)
    ResponseEntity<ErrorResponse> queryAssetMismatch() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误。");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式或参数不合法。");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> internalError(Exception e) {
        LOG.error("Unhandled API error: {}", e.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误。");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}