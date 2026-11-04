package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantImpactResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantMutationResult;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-058·060: 참여 행은 보존하고 현재 유효 집계에서 제외한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantRemovalService {
	private final MembershipService memberships;
	private final PerformanceRepository performances;
	private final ParticipantImpactService impact;
	private final EntityManager entityManager;

	/** 공연 참여 제외한다 */
	@Transactional
	public ParticipantMutationResult remove(
		Long clubId,
		Long performanceId,
		Long participantId,
		Long actorUserId,
		ParticipantRemovalRequest request,
		boolean reject
	) {
		// 본문 생략을 빈 요청으로 정규화하고 공연 행을 잠가 동시 결정을 직렬화한다.
		if (request == null) {
			request = new ParticipantRemovalRequest(null, null, List.of());
		}
		final ParticipantRemovalRequest body = request;
		var performance = performances.findWithLockByIdAndClub_Id(performanceId, clubId)
				.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "비활성 동아리에서는 변경할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		// 거절·타인 제거는 관리자에게, 본인 철회는 해당 소속에게 권한을 부여한다.
		PerformanceMember member = impact.require(clubId, performanceId, participantId);
		boolean self = member.getMembership().getUser().getId().equals(actorUserId);
		if (reject || !self) {
			memberships.requireAdmin(clubId, actorUserId);
		} else {
			memberships.requireMember(clubId, actorUserId);
		}
		// 거절은 신청 대기와 공연 버전을 요구하고 철회는 유효 참여 상태를 요구한다.
		if (reject) {
			if (body.performanceVersion() == null || !body.performanceVersion().matches("[0-9]+")) {
				throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "performanceVersion이 필요합니다.");
			}
			if (!body.performanceVersion().equals(performance.getVersion().toString())) {
				throw new ApiException(ApiErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
			}
			if (member.getStatus() != PerformanceMemberStatus.APPLIED) {
				throw new ApiException(ApiErrorCode.STATE_CONFLICT, "신청 대기 참여자만 거절할 수 있습니다.",
						"INVALID_STATE");
			}
		} else if (member.getStatus() != PerformanceMemberStatus.APPLIED
				&& member.getStatus() != PerformanceMemberStatus.APPROVED) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "철회할 수 없는 참여 상태입니다.",
					"INVALID_STATE");
		}
		// 공연 상태와 확정 공연의 필수 사유를 검증한다.
		PerformanceStatus status = performance.getStatus();
		if (status != PerformanceStatus.DRAFT && status != PerformanceStatus.ACTIVE
				&& status != PerformanceStatus.LOCKED) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "공연 상태가 변경을 허용하지 않습니다.",
					"INVALID_STATE");
		}
		if (status == PerformanceStatus.DRAFT && !reject && self) {
			throw new ApiException(ApiErrorCode.FORBIDDEN, "초안 공연에서는 임원만 참여자를 뺄 수 있습니다.");
		}
		if (status == PerformanceStatus.LOCKED && (body.reason() == null || body.reason().isBlank())) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "확정 공연 변경에는 사유가 필요합니다.",
					"REASON_REQUIRED");
		}
		if (body.reason() != null && body.reason().length() > 500) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "사유는 500자 이하여야 합니다.");
		}
		// 변경 전에 팀·곡·일정 영향을 계산해 응답과 안전 검증에 사용한다.
		ParticipantImpactResponse affected = impact.read(clubId, performanceId, participantId, actorUserId);
		if (!reject && status == PerformanceStatus.LOCKED) {
			@SuppressWarnings("unchecked")
			List<Number> emptySongs = entityManager.createNativeQuery("""
					SELECT DISTINCT s.id FROM song s
					JOIN song_session_member target ON target.song_id = s.id
					  AND target.performance_member_id = :memberId
					WHERE s.performance_id = :performanceId AND s.status = 'CONFIRMED'
					  AND NOT EXISTS (SELECT 1 FROM song_session_member other
						JOIN performance_member pm ON pm.id = other.performance_member_id
						WHERE other.song_id = s.id AND other.performance_member_id <> :memberId
						  AND pm.status = 'APPROVED')
					""").setParameter("memberId", participantId)
					.setParameter("performanceId", performanceId).getResultList();
			if (!emptySongs.isEmpty()) {
				throw new ApiException(ApiErrorCode.RULE_VIOLATION,
						"참여 해제 후 선정곡에 활동 연주자가 없습니다.", "SONG_WITHOUT_PLAYER");
			}
		}
		// 팀장 후임을 먼저 반영한 뒤 참여 상태를 거절 또는 철회로 변경한다.
		reassignLeaders(performanceId, participantId, body.teamLeaderSuccessors());
		member.transitionTo(reject ? PerformanceMemberStatus.REJECTED : PerformanceMemberStatus.WITHDRAWN,
				LocalDateTime.now(ZoneOffset.UTC));
		entityManager.persist(AuditLog.record(clubId, actorUserId,
				reject ? "PERFORMANCE_MEMBER_REJECTED" : "PERFORMANCE_MEMBER_WITHDRAWN",
				"PERFORMANCE_MEMBER", participantId, body.reason(),
				"{\"status\":\"" + member.getStatus() + "\"}"));
		if (status == PerformanceStatus.LOCKED) {
			entityManager.persist(AuditLog.record(clubId, actorUserId, "LOCKED_EXCEPTION_EDIT",
					"PERFORMANCE_MEMBER", participantId, body.reason(), "{}"));
		}
		entityManager.flush();
		return new ParticipantMutationResult(ParticipantResponse.from(member),
				affected.warnings().stream().map(warning -> (Object) warning).toList(),
				new ParticipantMutationResult.Effects(List.of(), affected.scheduleIds(),
						affected.affectedUserIds()));
	}

	/** 제외되는 참여자가 맡은 팀장 역할을 요청된 후임에게 넘긴다. */
	private void reassignLeaders(Long performanceId, Long participantId,
								 List<ParticipantRemovalRequest.Successor> supplied) {
		// 대상 참여자가 팀장인 팀과 요청된 후임 지정을 각각 집합으로 만든다.
		@SuppressWarnings("unchecked")
		List<Number> leaderTeamIds = entityManager.createNativeQuery("""
				SELECT id FROM team WHERE performance_id = :performanceId AND leader_member_id = :memberId
				""").setParameter("performanceId", performanceId).setParameter("memberId", participantId)
				.getResultList();
		Map<Long, Long> successors = new HashMap<>();
		if (supplied != null) {
			for (var item : supplied) {
				Long teamId = ApiIds.positive(item.teamId(), "teamLeaderSuccessors.teamId");
				Long membershipId = ApiIds.positive(item.membershipId(), "teamLeaderSuccessors.membershipId");
				if (successors.put(teamId, membershipId) != null) {
					throw successorError();
				}
			}
		}
		Set<Long> required = new HashSet<>(leaderTeamIds.stream().map(Number::longValue).toList());
		if (!successors.keySet().stream().allMatch(required::contains)) {
			throw successorError();
		}
		// 남은 팀원이 없으면 팀장을 비우고, 있으면 명시된 적격 후임으로 교체한다.
		for (Long teamId : required) {
			long remaining = ((Number) entityManager.createNativeQuery("""
					SELECT COUNT(*) FROM team_member WHERE team_id = :teamId
					  AND performance_member_id <> :memberId
					""").setParameter("teamId", teamId).setParameter("memberId", participantId)
					.getSingleResult()).longValue();
			if (remaining == 0) {
				if (successors.containsKey(teamId)) {
					throw successorError();
				}
				entityManager.createNativeQuery("""
					UPDATE team SET leader_member_id = NULL, version = version + 1 WHERE id = :teamId
					""").setParameter("teamId", teamId).executeUpdate();
			} else {
				Long membershipId = successors.get(teamId);
				if (membershipId == null) {
					throw successorError();
				}
				@SuppressWarnings("unchecked")
				List<Number> candidates = entityManager.createNativeQuery("""
						SELECT pm.id FROM team_member tm JOIN performance_member pm
						  ON pm.id = tm.performance_member_id
						JOIN membership m ON m.id = pm.membership_id
						WHERE tm.team_id = :teamId AND pm.id <> :memberId
						  AND m.id = :membershipId AND m.member_type = 'REGULAR'
						  AND m.status IN ('ACTIVE','ON_LEAVE')
						  AND pm.status IN ('APPLIED','APPROVED')
						""").setParameter("teamId", teamId).setParameter("memberId", participantId)
						.setParameter("membershipId", membershipId).getResultList();
				if (candidates.isEmpty()) {
					throw successorError();
				}
				entityManager.createNativeQuery("""
						UPDATE team SET leader_member_id = :leaderId, version = version + 1
						WHERE id = :teamId
						""").setParameter("leaderId", candidates.get(0).longValue())
						.setParameter("teamId", teamId).executeUpdate();
			}
		}
	}

	/** 팀장 후임 지정 오류를 일관된 API 예외로 생성한다. */
	private static ApiException successorError() {
		return new ApiException(ApiErrorCode.VALIDATION_ERROR, "팀장 후임 지정이 유효하지 않습니다.",
				"teamLeaderSuccessors", "SUCCESSOR_REQUIRED");
	}
}
