package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantListItem;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantListResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-053: 소속 상태와 사진 공개 범위를 응답에서 분리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantListReader {
	private final MembershipService memberships;
	private final PerformanceService performances;
	private final PerformanceMemberRepository repository;
	private final MembershipPartReader parts;
	private final EntityManager entityManager;

	/** 공연 참여 정보를 조회한다 */
	@Transactional(readOnly = true)
	public ParticipantListResponse read(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		String statuses,
		String q,
		String partIds,
		int page,
		int size
	) {
		// 페이지·검색어 범위를 먼저 검증하고 요청자의 공연 열람 권한을 확인한다.
		if (page < 0 || size < 1 || size > 100 || (q != null && (q.isBlank() || q.length() > 100))) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "목록 필터가 유효하지 않습니다.");
		}
		var actor = memberships.requireMember(clubId, actorUserId);
		performances.detail(clubId, performanceId, actorUserId);
		boolean admin = actor.getRole() == MembershipRole.CLUB_ADMIN;
		// 관리자는 요청 상태를, 일반 사용자는 승인 상태만 조회하도록 범위를 제한한다.
		Set<PerformanceMemberStatus> selectedStatuses = admin
				? (statuses == null ? EnumSet.allOf(PerformanceMemberStatus.class) : parseStatuses(statuses))
				: EnumSet.of(PerformanceMemberStatus.APPROVED);
		Set<String> selectedParts = partIds == null ? Set.of()
				: Arrays.stream(partIds.split(",", -1))
						.map(value -> ApiIds.positive(value, "partId").toString()).collect(Collectors.toSet());
		// 참여자와 파트를 일괄 조회하고 객원에게는 공동 팀·곡 참여자만 허용한다.
		List<PerformanceMember> all = repository.findByPerformance_IdAndClubId(performanceId, clubId);
		Set<Long> guestVisible = actor.getMemberType() == MemberType.GUEST
				? guestVisible(performanceId, actor.getId()) : null;
		Map<Long, List<PartResponse>> partMap = parts.partsOfAll(all.stream()
				.map(member -> member.getMembership().getId()).toList());
		String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
		// 상태·객원 범위·검색어·파트 필터를 적용하고 표시명 순으로 정렬한다.
		List<PerformanceMember> filtered = all.stream()
				.filter(member -> selectedStatuses.contains(member.getStatus()))
				.filter(member -> guestVisible == null || guestVisible.contains(member.getId()))
				.filter(member -> query.isEmpty() || displayName(member).toLowerCase(Locale.ROOT).contains(query))
				.filter(member -> selectedParts.isEmpty() || partMap.getOrDefault(
						member.getMembership().getId(), List.of()).stream()
						.anyMatch(part -> selectedParts.contains(part.getId())))
				.sorted(Comparator.comparing((PerformanceMember member) -> displayName(member)
						.toLowerCase(Locale.ROOT)).thenComparing(PerformanceMember::getId)).toList();
		int start = (int) Math.min((long) page * size, filtered.size());
		int end = Math.min(start + size, filtered.size());
		// 현재 페이지를 응답 DTO로 변환하면서 역할에 따라 민감 필드를 숨긴다.
		List<ParticipantListItem> items = filtered.subList(start, end).stream().map(member -> {
			Long membershipId = member.getMembership().getId();
			List<ParticipantListItem.Part> memberParts = partMap.getOrDefault(membershipId, List.of())
					.stream().map(part -> new ParticipantListItem.Part(part.getId(), part.getName())).toList();
			return new ParticipantListItem(member.getId().toString(), performanceId.toString(),
					membershipId.toString(), member.getStatus(), admin ? member.getSource() : null,
					member.getCreatedAt().atOffset(ZoneOffset.UTC)
							.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
					member.getUpdatedAt().atOffset(ZoneOffset.UTC)
							.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
					displayName(member), member.getMembership().getUser().getId().equals(actorUserId)
							? member.getMembership().getUser().getProfileImageUrl() : null,
					member.getMembership().getMemberType(),
					admin ? member.getMembership().getStatus() : null, memberParts,
					teamNames(member.getId()), playedSongCount(member.getId()));
		}).toList();
		// 화면 탭에 사용할 상태별 전체 개수를 권한 범위 안에서 계산한다.
		Map<String, Long> counts = new java.util.LinkedHashMap<>();
		for (PerformanceMemberStatus status : PerformanceMemberStatus.values()) {
			if (admin || status == PerformanceMemberStatus.APPROVED) {
				counts.put(status.name(), all.stream()
						.filter(member -> guestVisible == null || guestVisible.contains(member.getId()))
						.filter(member -> member.getStatus() == status).count());
			}
		}
		return new ParticipantListResponse(items, counts, page, size, filtered.size(),
				(filtered.size() + size - 1) / size);
	}

	/** 객원 소속이 볼 수 있는 참여자 식별자 범위를 계산한다. */
	private Set<Long> guestVisible(Long performanceId, Long membershipId) {
		// 객원 본인의 참여 행이 없으면 어떤 참여자도 공개하지 않는다.
		Long selfId = repository.findByPerformance_IdAndMembership_Id(performanceId, membershipId)
				.map(PerformanceMember::getId).orElse(null);
		if (selfId == null) {
			return Set.of();
		}
		// 본인과 같은 팀 또는 같은 곡을 연주하는 승인 참여자 ID를 조회한다.
		@SuppressWarnings("unchecked")
		List<Number> ids = entityManager.createNativeQuery("""
				SELECT DISTINCT pm.id FROM performance_member pm WHERE pm.performance_id = :performanceId
				  AND pm.status = 'APPROVED' AND (pm.id = :selfId
					OR EXISTS (SELECT 1 FROM team_member mine JOIN team_member peer
					  ON peer.team_id = mine.team_id WHERE mine.performance_member_id = :selfId
					  AND peer.performance_member_id = pm.id)
					OR EXISTS (SELECT 1 FROM song_session_member mine JOIN song_session_member peer
					  ON peer.song_id = mine.song_id WHERE mine.performance_member_id = :selfId
					  AND peer.performance_member_id = pm.id))
				""").setParameter("performanceId", performanceId).setParameter("selfId", selfId)
				.getResultList();
		return ids.stream().map(Number::longValue).collect(Collectors.toSet());
	}

	/** 참여자가 속한 팀 이름을 조회한다. */
	private List<String> teamNames(Long participantId) {
		@SuppressWarnings("unchecked")
		List<String> names = entityManager.createNativeQuery("""
				SELECT t.name FROM team_member tm JOIN team t ON t.id = tm.team_id
				WHERE tm.performance_member_id = :id ORDER BY t.name
				""").setParameter("id", participantId).getResultList();
		return names;
	}

	/** 참여자가 실제 연주자로 배정된 곡 수를 집계한다. */
	private long playedSongCount(Long participantId) {
		return ((Number) entityManager.createNativeQuery("""
				SELECT COUNT(DISTINCT ssm.song_id) FROM song_session_member ssm
				JOIN song s ON s.id = ssm.song_id WHERE ssm.performance_member_id = :id
				AND s.status = 'CONFIRMED'
				""").setParameter("id", participantId).getSingleResult()).longValue();
	}

	/** 참여자의 동아리 표시명 또는 사용자 기본 이름을 반환한다. */
	private static String displayName(PerformanceMember member) {
		return member.getMembership().getDisplayName() == null
				? member.getMembership().getUser().getName() : member.getMembership().getDisplayName();
	}

	/** 쉼표로 전달된 참여 상태 필터를 검증해 집합으로 변환한다. */
	private static Set<PerformanceMemberStatus> parseStatuses(String raw) {
		try {
			EnumSet<PerformanceMemberStatus> set = EnumSet.noneOf(PerformanceMemberStatus.class);
			Arrays.stream(raw.split(",", -1)).forEach(value -> set.add(PerformanceMemberStatus.valueOf(value)));
			if (set.isEmpty()) {
				throw new IllegalArgumentException();
			}
			return set;
		} catch (IllegalArgumentException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 유효하지 않습니다.");
		}
	}
}
