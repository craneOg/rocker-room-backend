package com.crane.bandwebbackend.global.error;

import com.crane.bandwebbackend.global.response.Meta;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/** 도메인 API 예외를 공통 오류 응답으로 변환한다. */
@RestControllerAdvice(basePackages = {
		"com.crane.bandwebbackend.domain.membership.controller",
		"com.crane.bandwebbackend.domain.performance.controller",
		"com.crane.bandwebbackend.domain.participant.controller",
		"com.crane.bandwebbackend.domain.schedule.controller",
		"com.crane.bandwebbackend.domain.audition.controller",
		"com.crane.bandwebbackend.domain.part.controller"
})
@Slf4j
public class ApiExceptionHandler {
	/** 비즈니스 오류를 API 공통 오류 응답으로 변환한다. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, Object>> handle(ApiException exception, HttpServletRequest request) {
		log.warn("API business error: {}", exception.getCode());
		if (exception instanceof ConfirmationRequiredException confirmation) {
			Map<String, Object> error = new LinkedHashMap<>();
			error.put("code", ApiErrorCode.CONFIRMATION_REQUIRED.name());
			error.put("message", confirmation.getMessage());
			error.put("details", List.of());
			error.put("warnings", confirmation.getWarnings());
			error.put("requestId", Meta.current().getRequestId());
			return ResponseEntity.status(ApiErrorCode.CONFIRMATION_REQUIRED.status())
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("error", error));
		}
		List<Map<String, String>> details = exception.getReason() == null ? List.of()
				: List.of(Map.of("field", exception.getField() == null ? "request" : exception.getField(),
						"reason", exception.getReason()));
		return problem(exception.getCode(), exception.getMessage(), details, request);
	}

	/** 요청 바디 검증 오류를 공통 형식으로 반환한다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
																  HttpServletRequest request) {
		log.warn("API validation error: {} fields", exception.getBindingResult().getFieldErrorCount());
		List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "reason", "INVALID_VALUE"))
				.toList();
		return problem(ApiErrorCode.VALIDATION_ERROR, "요청 값이 유효하지 않습니다.", fieldErrors, request);
	}

	/** 읽을 수 없는 요청 형식을 검증 오류로 반환한다. */
	@ExceptionHandler({HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception, HttpServletRequest request) {
		log.warn("Malformed API request: {}", exception.getClass().getSimpleName());
		return problem(ApiErrorCode.VALIDATION_ERROR, "요청 형식이 유효하지 않습니다.", List.of(), request);
	}

	/** 누락된 요청 헤더에 맞는 오류를 반환한다. */
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException exception,
																	HttpServletRequest request) {
		log.warn("Missing API request header: {}", exception.getHeaderName());
		if ("X-Requested-With".equalsIgnoreCase(exception.getHeaderName())) {
			return problem(ApiErrorCode.FORBIDDEN, "요청 헤더가 필요합니다.", List.of(), request);
		}
		return problem(ApiErrorCode.VALIDATION_ERROR, "요청 헤더가 필요합니다.", List.of(), request);
	}

	/** 지원하지 않는 본문 형식을 검증 오류로 반환한다. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException exception,
																HttpServletRequest request) {
		log.warn("Unsupported API content type");
		return problem(ApiErrorCode.VALIDATION_ERROR, "지원하지 않는 Content-Type입니다.",
				List.of(Map.of("field", "Content-Type", "reason", "UNSUPPORTED_MEDIA_TYPE")), request);
	}

	/** 잘못된 도메인 입력을 공통 검증 오류로 변환한다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(
			IllegalArgumentException exception, HttpServletRequest request) {
		log.warn("Invalid API domain argument");
		return problem(ApiErrorCode.VALIDATION_ERROR, "요청 값이 유효하지 않습니다.", List.of(), request);
	}

	/** DB 제약 오류의 내부 메시지를 숨기고 공통 충돌 오류로 변환한다. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrity(
			DataIntegrityViolationException exception, HttpServletRequest request) {
		log.warn("API database constraint violation");
		ApiErrorCode code = sqlErrorCode(exception) == 1062
				? ApiErrorCode.DUPLICATE : ApiErrorCode.STATE_CONFLICT;
		String message = code == ApiErrorCode.DUPLICATE
				? "이미 존재하는 값입니다." : "요청을 처리할 수 없는 상태입니다.";
		return problem(code, message,
				List.of(Map.of("field", "request", "reason", "CONSTRAINT_VIOLATION")), request);
	}

	private int sqlErrorCode(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException) {
				return sqlException.getErrorCode();
			}
		}
		return 0;
	}

	/** 낙관적 잠금 충돌을 버전 충돌 응답으로 변환한다. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, Object>> handleOptimisticLock(
		OptimisticLockingFailureException exception, HttpServletRequest request) {
		log.warn("API optimistic lock conflict");
		return problem(ApiErrorCode.VERSION_CONFLICT,
				"다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.", List.of(), request);
	}

	/** 예상하지 못한 오류는 내부 메시지와 코드 없이 공통 500 본문으로 숨긴다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected API error", exception);
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("message", "서버 내부 오류가 발생했습니다.");
		error.put("requestId", Meta.current().getRequestId());
		return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("error", error));
	}

	private ResponseEntity<Map<String, Object>> problem(ApiErrorCode code, String detail,
														 List<?> fieldErrors, HttpServletRequest request) {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("code", code.name());
		error.put("message", detail);
		error.put("details", fieldErrors);
		error.put("requestId", Meta.current().getRequestId());
		return ResponseEntity.status(code.status())
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("error", error));
	}
}
