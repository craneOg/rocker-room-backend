package com.crane.bandwebbackend.global.error;

public class ApiException extends RuntimeException {
	private final ApiErrorCode code;
	private final String reason;
	private final String field;

	public ApiException(ApiErrorCode code, String message) {
		super(message);
		this.code = code;
		this.reason = null;
		this.field = null;
	}

	public ApiException(ApiErrorCode code, String message, String reason) {
		super(message);
		this.code = code;
		this.reason = reason;
		this.field = null;
	}

	public ApiException(ApiErrorCode code, String message, String field, String reason) {
		super(message);
		this.code = code;
		this.field = field;
		this.reason = reason;
	}

	public ApiErrorCode getCode() {
		return code;
	}

	public String getReason() {
		return reason;
	}

	public String getField() {
		return field;
	}
}
