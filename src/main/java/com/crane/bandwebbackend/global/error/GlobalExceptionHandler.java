package com.crane.bandwebbackend.global.error;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";
    private static final String INVALID_VALUE = "INVALID_VALUE";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        return respond(e.getErrorCode(), e.getMessage(), e.getDetails(), e.getWarnings());
    }

    // 모든 필드 오류를 details에 담는다. 입력값은 싣지 않는다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        List<ErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> ErrorDetail.of(f.getField(), INVALID_VALUE))
                .toList();
        return respondValidation(INVALID_REQUEST, details);
    }

    // 경로·쿼리 파라미터 검증(@Validated)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        List<ErrorDetail> details = e.getConstraintViolations().stream()
                .map(v -> ErrorDetail.of(v.getPropertyPath().toString(), INVALID_VALUE))
                .toList();
        return respondValidation(INVALID_REQUEST, details);
    }

    // 컨트롤러 파라미터의 제약 검증(@RequestParam @Min 등, Spring 6.2). 이 예외도 400 VALIDATION_ERROR로 돌려준다
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handle(HandlerMethodValidationException e) {
        List<ErrorDetail> details = e.getParameterValidationResults().stream()
                .map(r -> ErrorDetail.of(r.getMethodParameter().getParameterName(), INVALID_VALUE))
                .toList();
        return respondValidation(INVALID_REQUEST, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentTypeMismatchException e) {
        return respondValidation(INVALID_REQUEST, List.of(ErrorDetail.of(e.getName(), INVALID_VALUE)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handle(MissingServletRequestParameterException e) {
        return respondValidation(INVALID_REQUEST, List.of(ErrorDetail.of(e.getParameterName(), INVALID_VALUE)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handle(MissingRequestHeaderException e) {
        if ("X-Requested-With".equalsIgnoreCase(e.getHeaderName())) {
            return respond(ErrorCode.FORBIDDEN, "요청 헤더가 필요합니다.");
        }
        return respondValidation(INVALID_REQUEST, List.of(ErrorDetail.of(e.getHeaderName(), INVALID_VALUE)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return respondValidation("요청 형식이 올바르지 않습니다.", List.of());
    }

    // 415는 쓰지 않는다. 오류 code는 9종뿐이라 400 VALIDATION_ERROR로 돌려준다(U5)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMediaTypeNotSupportedException e) {
        return respondValidation("지원하지 않는 요청 형식입니다.", List.of(ErrorDetail.of(null, "UNSUPPORTED_MEDIA_TYPE")));
    }

    // 낙관적 락 대상(performance, team, song, practice_schedule)
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handle(OptimisticLockingFailureException e) {
        return respond(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");
    }

    // 안전망: 서비스가 먼저 검사해 BusinessException으로 바꿔야 하는 DB 제약 위반(1062, 1451, 1452, 3819).
    // 여기까지 왔다는 것은 서비스 검증 누락이므로 오류로 기록한다. 원인 메시지에 입력값이 들어 있어 응답·로그에 싣지 않는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException e) {
        int sqlErrorCode = findSqlErrorCode(e);
        log.error("DB 제약 위반이 서비스 검증을 통과했습니다. sqlErrorCode={}", sqlErrorCode);
        return switch (sqlErrorCode) {
            case 1062 -> respond(ErrorCode.DUPLICATE, "이미 존재하는 값입니다.");
            case 3819 -> respond(ErrorCode.RULE_VIOLATION, "업무 규칙에 맞지 않는 값입니다.");
            default -> respond(ErrorCode.STATE_CONFLICT, "현재 상태에서는 처리할 수 없습니다.");
        };
    }

    // 없는 경로. 안 잡으면 아래 catch-all이 500으로 바꾼다
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
    }

    // 예상하지 못한 오류. code·details 없이 message·requestId만 내리고, 예외 내용은 로그에만 남긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 405 등 프레임워크가 상태를 정한 요청 오류는 500으로 바꾸지 않는다(code 없음)
        if (e instanceof org.springframework.web.ErrorResponse framework
                && framework.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(framework.getStatusCode())
                    .headers(framework.getHeaders())
                    .body(ErrorResponse.withoutCode("요청을 처리할 수 없습니다."));
        }
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.withoutCode("서버 내부 오류가 발생했습니다."));
    }

    private int findSqlErrorCode(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlException) {
                return sqlException.getErrorCode();
            }
        }
        return 0;
    }

    private ResponseEntity<ErrorResponse> respondValidation(String message, List<ErrorDetail> details) {
        return respond(ErrorCode.VALIDATION_ERROR, message, details, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
        return respond(code, message, List.of(), List.of());
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode code, String message, List<ErrorDetail> details, List<?> warnings) {
        HttpStatusCode status = code.getStatus();
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, details, warnings));
    }
}
