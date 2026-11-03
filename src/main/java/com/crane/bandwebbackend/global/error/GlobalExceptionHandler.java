package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.response.RequestIds;
import com.crane.bandwebbackend.global.response.Warning;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 공통 오류 본문(API 명세 공통 규칙): {error:{code, message, details[{field, reason}], requestId, warnings?}}
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        List<ErrorResponse.Detail> details = e.getReason() == null
                ? List.of()
                : List.of(ErrorResponse.Detail.builder().reason(e.getReason()).build());
        List<Warning> warnings = e.getErrorCode() == ErrorCode.CONFIRMATION_REQUIRED ? e.getWarnings() : null;
        return respond(e.getErrorCode(), e.getMessage(), details, warnings);
    }

    // 필드별 오류는 details에, 메시지는 첫 필드 오류
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse(INVALID_REQUEST);
        List<ErrorResponse.Detail> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> ErrorResponse.Detail.builder()
                        .field(f.getField())
                        .reason(f.getCode())
                        .build())
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, message, details, null);
    }

    // 경로·쿼리 파라미터 검증(@Validated)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse(INVALID_REQUEST);
        return respond(ErrorCode.VALIDATION_ERROR, message, List.of(), null);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return respond(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.", List.of(), null);
    }

    // 낙관적 락 대상(performance, team, song, practice_schedule)
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handle(OptimisticLockingFailureException e) {
        return respond(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.", List.of(), null);
    }

    // 안전망: 서비스가 먼저 검사해 BusinessException으로 바꿔야 하는 DB 제약 위반(1062, 1451, 1452, 3819).
    // 여기까지 왔다는 것은 서비스 검증 누락이므로 오류로 기록한다. 원인 메시지에 입력값이 들어 있어 응답·로그에 싣지 않는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException e) {
        int sqlErrorCode = findSqlErrorCode(e);
        log.error("DB 제약 위반이 서비스 검증을 통과했습니다. sqlErrorCode={}", sqlErrorCode);
        return switch (sqlErrorCode) {
            case 1062 -> respond(ErrorCode.DUPLICATE, "이미 존재하는 값입니다.", List.of(), null);
            case 3819 -> respond(ErrorCode.RULE_VIOLATION, "업무 규칙에 맞지 않는 값입니다.", List.of(), null);
            default -> respond(ErrorCode.STATE_CONFLICT, "현재 상태에서는 처리할 수 없습니다.", List.of(), null);
        };
    }

    private int findSqlErrorCode(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlException) {
                return sqlException.getErrorCode();
            }
        }
        return 0;
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode code, String message, List<ErrorResponse.Detail> details, List<Warning> warnings) {
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(ErrorResponse.Body.builder()
                        .code(code.name())
                        .message(message)
                        .details(details)
                        .requestId(RequestIds.current())
                        .warnings(warnings)
                        .build()));
    }
}
