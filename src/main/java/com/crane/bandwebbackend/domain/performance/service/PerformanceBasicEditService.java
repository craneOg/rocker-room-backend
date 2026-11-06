package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceUpdateResponse;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import jakarta.persistence.EntityManager;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceBasicEditService {
	private final MembershipService memberships;
	private final PerformanceRepository performances;
	private final PerformancePolicyRepository policies;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** 공연 수정한다 */
	@Transactional
	public PerformanceUpdateResponse update(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		JsonNode body
	) {
		// 관리자 권한과 요청 JSON·필수 버전 형식을 검증한다.
		memberships.requireAdmin(clubId, actorUserId);
		if (body == null || !body.isObject() || !body.hasNonNull("version")) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "version이 필요합니다.");
		}
		// 현재 공연을 조회해 동아리·상태·낙관적 버전 조건을 확인한다.
		Performance performance = performances.findByIdAndClub_Id(performanceId, clubId)
				.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "비활성 동아리에서는 수정할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		String version = body.path("version").asText();
		if (!version.matches("[0-9]+")) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "version 형식이 유효하지 않습니다.");
		}
		if (!version.equals(String.valueOf(performance.getVersion()))) {
			throw new ApiException(ApiErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
		}
		if (performance.getStatus() == PerformanceStatus.FINISHED
				|| performance.getStatus() == PerformanceStatus.CANCELED) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "수정할 수 없는 공연입니다.", "INVALID_STATE");
		}
		// 확정 공연 수정에는 필수 사유를 요구하고 선택 필드는 기존 값으로 보완한다.
		String reason = body.has("reason") ? string(body.get("reason"), 500, true) : null;
		if (performance.getStatus() == PerformanceStatus.LOCKED && (reason == null || reason.isBlank())) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "확정 공연 수정에는 사유가 필요합니다.",
					"REASON_REQUIRED");
		}
		String title = body.has("title") ? string(body.get("title"), 200, false) : performance.getTitle();
		LocalDate date = body.has("performDate") ? date(body.get("performDate")) : performance.getPerformDate();
		String location = body.has("location") ? string(body.get("location"), 200, true)
				: performance.getLocation();
		// 엔티티 규칙으로 기본 정보를 변경하고 도메인 예외를 API 오류로 변환한다.
		try {
			performance.updateDetails(title, date, location);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, exception.getMessage());
		}
		// 일반 수정과 확정 공연 예외 수정을 각각 감사 로그에 기록한다.
		auditRecorder.record(clubId, actorUserId, "PERFORMANCE_UPDATED", "PERFORMANCE", performanceId, reason, Map.of("version", version));
		if (performance.getStatus() == PerformanceStatus.LOCKED) {
			auditRecorder.record(clubId, actorUserId, "LOCKED_EXCEPTION_EDIT", "PERFORMANCE", performanceId, reason, Map.of("version", version));
		}
		entityManager.flush();
		// 변경된 공연일과 합주 시즌의 불일치는 차단하지 않고 경고로 반환한다.
		List<PerformanceUpdateResponse.Warning> warnings = new ArrayList<>();
		policies.findById(performanceId).ifPresent(policy -> {
			if (date != null && policy.getPracticeSeasonEnd() != null
					&& policy.getPracticeSeasonEnd().isAfter(date)) {
				warnings.add(new PerformanceUpdateResponse.Warning("SEASON_ENDS_AFTER_PERFORM_DATE",
						"합주 시즌이 공연일 이후까지예요."));
			}
		});
		return PerformanceUpdateResponse.from(PerformanceResponse.from(performance), warnings);
	}

	/** JSON 문자열을 null 허용 여부와 최대 길이에 맞게 검증한다. */
	private static String string(JsonNode value, int max, boolean nullable) {
		if (value.isNull() && nullable) {
			return null;
		}
		if (!value.isTextual() || value.asText().length() > max
				|| (!nullable && value.asText().isBlank())) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "문자열 필드가 유효하지 않습니다.");
		}
		return value.asText();
	}

	/** JSON 날짜 문자열을 ISO 지역 날짜로 변환한다. */
	private static LocalDate date(JsonNode value) {
		if (value.isNull()) {
			return null;
		}
		if (!value.isTextual()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "performDate가 유효하지 않습니다.");
		}
		try {
			return LocalDate.parse(value.asText());
		} catch (DateTimeException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "performDate가 유효하지 않습니다.");
		}
	}
}
