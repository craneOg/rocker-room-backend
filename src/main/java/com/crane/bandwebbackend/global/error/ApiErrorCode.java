package com.crane.bandwebbackend.global.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	STATE_CONFLICT(HttpStatus.CONFLICT),
	DUPLICATE(HttpStatus.CONFLICT),
	VERSION_CONFLICT(HttpStatus.CONFLICT),
	CONFIRMATION_REQUIRED(HttpStatus.CONFLICT),
	RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY);

	private final HttpStatus status;

	ApiErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
