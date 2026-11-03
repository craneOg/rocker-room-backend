package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.response.RequestIds;
import com.crane.bandwebbackend.global.response.Warning;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        List<ErrorResponse.Detail> details = e.getReason() == null
                ? List.of()
                : List.of(ErrorResponse.Detail.builder().reason(e.getReason()).build());
        List<Warning> warnings = e.getErrorCode() == ErrorCode.CONFIRMATION_REQUIRED ? e.getWarnings() : null;
        return respond(e.getErrorCode(), e.getMessage(), details, warnings);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        List<ErrorResponse.Detail> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> ErrorResponse.Detail.builder()
                        .field(f.getField())
                        .reason(f.getCode())
                        .build())
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", details, null);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return respond(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.", List.of(), null);
    }

    // 낙관적 락 대상(performance, team, song, practice_schedule)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handle(ObjectOptimisticLockingFailureException e) {
        return respond(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.", List.of(), null);
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
