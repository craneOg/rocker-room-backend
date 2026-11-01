package com.crane.bandwebbackend.global.error;

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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        return respond(e.getErrorCode(), e.getMessage(), e.getWarnings());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse(INVALID_REQUEST);
        return respond(ErrorCode.VALIDATION_ERROR, message, List.of());
    }

    // 경로·쿼리 파라미터 검증(@Validated)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse(INVALID_REQUEST);
        return respond(ErrorCode.VALIDATION_ERROR, message, List.of());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return respond(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.", List.of());
    }

    // 낙관적 락 대상(performance, team, song, practice_schedule)
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handle(OptimisticLockingFailureException e) {
        return respond(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.", List.of());
    }

    // 안전망: 서비스가 먼저 검사해 BusinessException으로 바꿔야 하는 DB 제약 위반(1062, 1451, 1452, 3819).
    // 여기까지 왔다는 것은 서비스 검증 누락이므로 오류로 기록한다. 원인 메시지에 입력값이 들어 있어 응답·로그에 싣지 않는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException e) {
        int sqlErrorCode = findSqlErrorCode(e);
        log.error("DB 제약 위반이 서비스 검증을 통과했습니다. sqlErrorCode={}", sqlErrorCode);
        return switch (sqlErrorCode) {
            case 1062 -> respond(ErrorCode.DUPLICATE, "이미 존재하는 값입니다.", List.of());
            case 3819 -> respond(ErrorCode.RULE_VIOLATION, "업무 규칙에 맞지 않는 값입니다.", List.of());
            default -> respond(ErrorCode.STATE_CONFLICT, "현재 상태에서는 처리할 수 없습니다.", List.of());
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

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, List<String> warnings) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.builder()
                        .code(code.name())
                        .message(message)
                        .warnings(warnings)
                        .build());
    }
}
