package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import com.crane.bandwebbackend.domain.membership.dto.MembershipPublicItem;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-020·022. 공개 목록과 임원 목록은 서버에서 필드·필터를 분리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MemberDirectoryService {
	private final MembershipService membershipService;
	private final MembershipRepository repository;
	private final MembershipPartReader partReader;
	private final CoreEntityLookupService entityLookup;

	@Transactional(readOnly = true)
	public Object detail(Long clubId, Long membershipId, Long actorUserId) {
		Membership member = membershipService.detail(clubId, membershipId, actorUserId);
		List<PartResponse> parts = partReader.partsOf(membershipId);
		if (member.getUser().getId().equals(actorUserId)) {
			return MembershipResponse.from(member, parts, actorUserId);
		}
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		boolean admin = actor.getRole() == MembershipRole.CLUB_ADMIN && actor.getMemberType() == MemberType.REGULAR
				&& (actor.getStatus() == MembershipStatus.ACTIVE || actor.getStatus() == MembershipStatus.ON_LEAVE);
		return admin ? MembershipResponse.from(member, parts, actorUserId)
				: MembershipPublicItem.from(member, parts, actorUserId);
	}

	/** 소속 목록을 조회한다 */
	@Transactional(readOnly = true)
	public Page<Object> list(
		Long clubId,
		Long actorUserId,
		String q,
		String statuses,
		MembershipRole role,
		MemberType type,
		String partIds,
		Pageable pageable
	) {
		// 요청자의 소속과 역할을 읽어 관리자 전용 필터 사용 가능 여부를 결정한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		boolean admin = actor.getRole() == MembershipRole.CLUB_ADMIN;
		if (!admin && actor.getMemberType() != MemberType.REGULAR) {
			throw new ApiException(ApiErrorCode.NOT_FOUND, "부원 목록을 찾을 수 없습니다.");
		}
		if (!admin && statuses != null) {
			throw new ApiException(ApiErrorCode.FORBIDDEN, "부원은 상태 필터를 사용할 수 없습니다.");
		}
		if (q != null && q.length() > 100) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "q는 100자 이하여야 합니다.");
		}
		// 관리자와 일반 부원에게 서로 다른 기본 상태 범위를 적용한다.
		Set<MembershipStatus> statusFilter = admin
				? parseStatuses(statuses == null ? "ACTIVE" : statuses)
				: EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE, MembershipStatus.GRADUATED);
		Set<String> selectedParts = partIds == null ? Set.of() : Arrays.stream(partIds.split(",", -1))
				.map(value -> ApiIds.positive(value, "partId").toString()).collect(Collectors.toSet());
		// 소속과 파트를 일괄 조회해 필터 과정의 N+1 쿼리를 피한다.
		List<Membership> all = repository.findByClub_Id(clubId);
		Map<Long, List<PartResponse>> parts = partReader.partsOfAll(all.stream().map(Membership::getId).toList());
		String query = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
		// 상태·역할·유형·검색어·파트를 모두 적용한 뒤 관리자와 이름 순으로 정렬한다.
		List<Membership> selected = all.stream()
				.filter(member -> statusFilter.contains(member.getStatus()))
				.filter(member -> role == null || member.getRole() == role)
				.filter(member -> type == null || member.getMemberType() == type)
				.filter(member -> query.isEmpty() || displayName(member).toLowerCase(Locale.ROOT).contains(query)
						|| member.getUser().getName().toLowerCase(Locale.ROOT).contains(query))
				.filter(member -> selectedParts.isEmpty() || parts.getOrDefault(member.getId(), List.of())
						.stream().anyMatch(part -> selectedParts.contains(part.getId())))
				.sorted(Comparator.comparing((Membership member) -> member.getRole() != MembershipRole.CLUB_ADMIN)
						.thenComparing(member -> displayName(member).toLowerCase(Locale.ROOT))
						.thenComparing(Membership::getId))
				.toList();
		// 메모리 필터 결과에서 요청 페이지 구간만 잘라 반환한다.
		int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), selected.size());
		int end = Math.min(start + pageable.getPageSize(), selected.size());
		List<Object> items = selected.subList(start, end).stream()
				.map(member -> (Object) (admin
						? MembershipResponse.from(member, parts.getOrDefault(member.getId(), List.of()), actorUserId)
						: MembershipPublicItem.from(member, parts.getOrDefault(member.getId(), List.of()), actorUserId)))
				.toList();
		return new PageImpl<>(items, pageable, selected.size());
	}

	/** 소속 사용자를 조회한다 */
	@Transactional(readOnly = true)
	public LookupResult lookup(
		Long clubId,
		Long actorUserId,
		String handle
	) {
		// 직접 초대에 사용되는 사용자 검색이므로 관리자에게만 허용한다.
		membershipService.requireAdmin(clubId, actorUserId);
		if (handle == null || handle.isBlank()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "handle이 필요합니다.");
		}
		// 활성 사용자를 찾고 현재 동아리의 재사용 가능한 소속 상태를 함께 조회한다.
		User user = entityLookup.byHandle(handle);
		Membership existing = repository.findByClub_IdAndUser_Id(clubId, user.getId()).orElse(null);
		if (existing != null && !EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE,
				MembershipStatus.GRADUATED, MembershipStatus.PENDING, MembershipStatus.INVITED)
				.contains(existing.getStatus())) {
			existing = null;
		}
		return new LookupResult(user, existing);
	}

	/** 쉼표로 전달된 소속 상태 필터를 검증해 집합으로 변환한다. */
	private static Set<MembershipStatus> parseStatuses(String raw) {
		if (raw.isBlank()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 비어 있습니다.");
		}
		try {
			EnumSet<MembershipStatus> result = EnumSet.noneOf(MembershipStatus.class);
			Arrays.stream(raw.split(",", -1)).forEach(value -> result.add(MembershipStatus.valueOf(value)));
			return result;
		} catch (IllegalArgumentException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 유효하지 않습니다.");
		}
	}

	/** 동아리 표시명이 없으면 사용자 기본 이름을 반환한다. */
	private static String displayName(Membership member) {
		return member.getDisplayName() == null ? member.getUser().getName() : member.getDisplayName();
	}

	public record LookupResult(User user, Membership membership) {
	}
}
