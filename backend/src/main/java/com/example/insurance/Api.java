package com.example.insurance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Schema(description = "统一响应结构；成功时 code=OK，错误时使用 HTTP 状态码和业务错误代码。")
public record Api<T>(
    @Schema(description = "业务响应代码", example = "OK") String code,
    @Schema(description = "错误描述；成功时为空字符串") String message,
    @Schema(description = "业务数据") T data) {
  public static <T> Api<T> ok(T data) {
    return new Api<>("OK", "", data);
  }

  public static Api<Void> ok() {
    return ok(null);
  }

  public static Failure bad(String message) {
    return new Failure(400, "INVALID_ARGUMENT", message);
  }

  public static Failure conflict(String message) {
    return new Failure(409, "CONFLICT", message);
  }

  public static Failure missing(String label) {
    return new Failure(404, "NOT_FOUND", label + "不存在");
  }

  public static Failure forbidden() {
    return new Failure(403, "FORBIDDEN", "没有执行此操作的权限");
  }

  public static class Failure extends RuntimeException {
    final int status;
    final String code;

    public Failure(int status, String code, String message) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }
}

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(Api.Failure.class)
  ResponseEntity<Api<Void>> domain(Api.Failure e) {
    return ResponseEntity.status(e.status).body(new Api<>(e.code, e.getMessage(), null));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Api<Void>> validation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .orElse("参数不正确");
    return ResponseEntity.badRequest().body(new Api<>("INVALID_ARGUMENT", message, null));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<Api<Void>> malformed(Exception e) {
    return ResponseEntity.badRequest().body(new Api<>("INVALID_ARGUMENT", "请求格式或参数不正确", null));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Api<Void>> duplicate(DataIntegrityViolationException e) {
    return ResponseEntity.status(409).body(new Api<>("CONFLICT", "编码、证件号、卡号或请求标识重复，请检查输入", null));
  }

  @ExceptionHandler(PessimisticLockingFailureException.class)
  ResponseEntity<Api<Void>> lock(Exception e) {
    return ResponseEntity.status(409)
        .body(new Api<>("CONCURRENT_REQUEST", "记录正在处理，请使用原 requestId 重试", null));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Api<Void>> size(Exception e) {
    return ResponseEntity.status(413).body(new Api<>("FILE_TOO_LARGE", "文件不能超过 10 MB", null));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Api<Void>> resourceMissing(NoResourceFoundException e) {
    return ResponseEntity.status(404).body(new Api<>("NOT_FOUND", "请求的资源不存在", null));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Api<Void>> unknown(Exception e) {
    LoggerFactory.getLogger(getClass()).error("Unhandled request error", e);
    return ResponseEntity.internalServerError()
        .body(new Api<>("INTERNAL_ERROR", "服务暂时无法处理，请稍后重试", null));
  }
}
