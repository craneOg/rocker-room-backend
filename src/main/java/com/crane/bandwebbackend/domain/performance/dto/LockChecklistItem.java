package com.crane.bandwebbackend.domain.performance.dto;

/** API-050·052에서 공유하는 점검 항목. type은 HTTP 오류 코드와 별개다. */
public record LockChecklistItem(String checkId, Result result, String type, String message,
								String targetType, String targetId) {
	public enum Result { SATISFIED, UNMET, WARNING }
}
