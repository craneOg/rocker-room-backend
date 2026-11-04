package com.crane.bandwebbackend.global.error;

import java.util.List;

/** 확인 요청의 경고 목록은 error.warnings에 포함한다. */
public class ConfirmationRequiredException extends ApiException {
	private final List<Object> warnings;

	public ConfirmationRequiredException(List<?> warnings) {
		super(ApiErrorCode.CONFIRMATION_REQUIRED, "경고를 확인한 뒤 다시 요청해 주세요.");
		this.warnings = List.copyOf(warnings);
	}

	public List<Object> getWarnings() {
		return warnings;
	}
}
