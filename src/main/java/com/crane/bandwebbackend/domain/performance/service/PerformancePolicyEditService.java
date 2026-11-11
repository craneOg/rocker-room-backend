package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformancePolicyResponse;
import com.crane.bandwebbackend.domain.performance.dto.PolicyUpdateResponse;
import com.crane.bandwebbackend.domain.performance.entity.CandidateScope;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-042: voteMethod 변경은 API-044만 허용한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class PerformancePolicyEditService {
    private final MembershipService memberships;
    private final PerformanceRepository performances;
    private final PerformancePolicyRepository policies;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 공연 수정한다 */
    @Transactional
    public PolicyUpdateResponse update(Long clubId, Long performanceId, Long actorUserId, JsonNode body) {
        // 관리자 권한과 정책 JSON 구조를 확인하고 투표 방식은 전용 API로 분리한다.
        memberships.requireAdmin(clubId, actorUserId);
        if (body == null || !body.isObject() || !body.hasNonNull("version") || body.has("voteMethod")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "정책 요청이 유효하지 않습니다.");
        }
        // 공연의 동아리·상태·버전을 검증해 오래된 정책 수정 요청을 차단한다.
        var performance = performances
                .findByIdAndClub_Id(performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
        if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 수정할 수 없습니다.");
        }
        if (performance.getStatus() != PerformanceStatus.DRAFT && performance.getStatus() != PerformanceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "정책을 변경할 수 없는 공연 상태입니다.");
        }
        String version = body.path("version").asText();
        if (!version.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "version 형식이 유효하지 않습니다.");
        }
        if (!version.equals(String.valueOf(performance.getVersion()))) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
        }
        PerformancePolicy policy = policies.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공연 정책을 찾을 수 없습니다."));
        // 한도와 시즌 축소 여부를 나중에 경고하기 위해 기존 값을 보존한다.
        Integer oldCandidateLimit = policy.getCandidateLimitPerMember();
        Integer oldVoteLimit = policy.getVoteMaxChoices();
        Integer oldSongLimit = policy.getMaxSongsPerMember();
        LocalDate oldSeasonStart = policy.getPracticeSeasonStart();
        LocalDate oldSeasonEnd = policy.getPracticeSeasonEnd();
        // 후보곡 범위 변경이 기존 후보곡의 팀 범위와 충돌하는지 검사한다.
        CandidateScope scope = value(body, "candidateScope", CandidateScope.class, policy.getCandidateScope());
        if (scope != policy.getCandidateScope()) {
            long incompatible = ((Number) entityManager
                            .createNativeQuery("""
					SELECT COUNT(*) FROM song WHERE performance_id = :id AND
					((:scope = 'ALL_PARTICIPANTS' AND candidate_team_id IS NOT NULL)
					 OR (:scope = 'TEAM_MEMBERS' AND candidate_team_id IS NULL))
					""")
                            .setParameter("id", performanceId)
                            .setParameter("scope", scope.name())
                            .getSingleResult())
                    .longValue();
            if (incompatible > 0) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "POLICY_SCOPE_CONFLICT", "기존 후보곡과 범위가 충돌합니다.");
            }
        }
        // 누락 필드는 기존 값으로 유지하면서 전체 정책 값 객체를 재구성한다.
        var values = new PerformancePolicy.Values(
                value(body, "applicationState", FeatureState.class, policy.getApplicationState()),
                time(body, "applicationDeadline", policy.getApplicationDeadline()),
                value(body, "candidateState", FeatureState.class, policy.getCandidateState()),
                time(body, "candidateDeadline", policy.getCandidateDeadline()),
                scope,
                integer(body, "candidateLimitPerMember", policy.getCandidateLimitPerMember()),
                value(body, "playabilityState", FeatureState.class, policy.getPlayabilityState()),
                time(body, "playabilityDeadline", policy.getPlayabilityDeadline()),
                value(body, "voteState", FeatureState.class, policy.getVoteState()),
                time(body, "voteDeadline", policy.getVoteDeadline()),
                policy.getVoteMethod(),
                integer(body, "voteMaxChoices", policy.getVoteMaxChoices()),
                bool(body, "playabilityResultPublic", policy.isPlayabilityResultPublic()),
                bool(body, "voteResultPublic", policy.isVoteResultPublic()),
                value(body, "teamApplicationState", FeatureState.class, policy.getTeamSubmissionState()),
                time(body, "teamApplicationDeadline", policy.getTeamSubmissionDeadline()),
                value(body, "availabilityState", FeatureState.class, policy.getAvailabilityState()),
                time(body, "availabilityDeadline", policy.getAvailabilityDeadline()),
                date(body, "practiceSeasonStart", policy.getPracticeSeasonStart()),
                date(body, "practiceSeasonEnd", policy.getPracticeSeasonEnd()),
                integer(body, "maxSongsPerMember", policy.getMaxSongsPerMember()));
        // 정책 엔티티의 교차 필드 규칙을 적용하고 예외 유형을 API 오류로 변환한다.
        try {
            policy.replace(values);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "PRACTICE_SEASON_REQUIRED", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
        }
        // 정책 변경도 공연 revision에 반영하고 감사 로그와 함께 즉시 저장한다.
        entityManager.lock(performance, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        auditRecorder.record(
                clubId,
                actorUserId,
                "PERFORMANCE_POLICY_UPDATED",
                "PERFORMANCE",
                performanceId,
                null,
                Map.of("version", version));
        entityManager.flush();
        entityManager.refresh(performance);
        // 기존 입력은 삭제하지 않으므로 축소된 기간·한도 영향을 경고로 알려 준다.
        List<PolicyUpdateResponse.Warning> warnings = new ArrayList<>();
        if (performance.getPerformDate() != null
                && values.practiceSeasonEnd() != null
                && values.practiceSeasonEnd().isAfter(performance.getPerformDate())) {
            warnings.add(new PolicyUpdateResponse.Warning("SEASON_ENDS_AFTER_PERFORM_DATE", "합주 시즌이 공연일 이후까지예요."));
        }
        if (decreased(oldCandidateLimit, values.candidateLimitPerMember())
                || decreased(oldVoteLimit, values.voteMaxChoices())
                || decreased(oldSongLimit, values.maxSongsPerMember())) {
            warnings.add(new PolicyUpdateResponse.Warning("LIMIT_REDUCED", "기존 입력은 유지되며 새 한도는 다음 입력부터 적용됩니다."));
        }
        if (oldSeasonStart != null
                && oldSeasonEnd != null
                && values.practiceSeasonStart() != null
                && (values.practiceSeasonStart().isAfter(oldSeasonStart)
                        || values.practiceSeasonEnd().isBefore(oldSeasonEnd))) {
            warnings.add(
                    new PolicyUpdateResponse.Warning("PRACTICE_SEASON_REDUCED", "시즌 밖의 기존 가능 시간은 유지되며 계산에서 제외됩니다."));
        }
        return new PolicyUpdateResponse(PerformancePolicyResponse.from(policy), warnings);
    }

    /** nullable 제한값이 이전보다 감소했는지 판별한다. */
    private static boolean decreased(Integer oldValue, Integer next) {
        return next != null && (oldValue == null || next < oldValue);
    }

    /** JSON에서 선택 enum 값을 읽고 누락 시 기존 값을 유지한다. */
    private static <T extends Enum<T>> T value(JsonNode body, String key, Class<T> type, T old) {
        if (!body.has(key)) {
            return old;
        }
        JsonNode node = body.get(key);
        if (!node.isTextual()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
    }

    /** JSON에서 양의 정수 정책값을 읽고 누락 시 기존 값을 유지한다. */
    private static Integer integer(JsonNode body, String key, Integer old) {
        if (!body.has(key)) {
            return old;
        }
        JsonNode node = body.get(key);
        if (node.isNull()) {
            return null;
        }
        if (!node.isInt()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
        return node.asInt();
    }

    /** JSON에서 불리언 정책값을 읽고 누락 시 기존 값을 유지한다. */
    private static boolean bool(JsonNode body, String key, boolean old) {
        if (!body.has(key)) {
            return old;
        }
        if (!body.get(key).isBoolean()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
        return body.get(key).asBoolean();
    }

    /** JSON에서 날짜 정책값을 읽고 누락 시 기존 값을 유지한다. */
    private static LocalDate date(JsonNode body, String key, LocalDate old) {
        if (!body.has(key)) {
            return old;
        }
        if (body.get(key).isNull()) {
            return null;
        }
        if (!body.get(key).isTextual()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
        try {
            return LocalDate.parse(body.get(key).asText());
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
    }

    /** JSON에서 시각 정책값을 UTC로 읽고 누락 시 기존 값을 유지한다. */
    private static LocalDateTime time(JsonNode body, String key, LocalDateTime old) {
        if (!body.has(key)) {
            return old;
        }
        if (body.get(key).isNull()) {
            return null;
        }
        if (!body.get(key).isTextual()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
        try {
            return OffsetDateTime.parse(body.get(key).asText())
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + "가 유효하지 않습니다.");
        }
    }
}
