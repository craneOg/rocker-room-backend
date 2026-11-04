package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceListItem;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceListReader {
	private final PerformanceService performances;
	private final MembershipService memberships;
	private final PerformancePolicyRepository policies;
	private final PerformanceMemberRepository participants;

	/** 공연 페이지를 조회한다 */
	@Transactional(readOnly = true)
	public Page<PerformanceListItem> page(
		Long clubId,
		Long actorUserId,
		String status,
		PerformancePreset preset,
		LocalDate from,
		LocalDate to,
		String sort,
		Pageable pageable
	) {
		// 날짜 범위와 상태·정렬 문자열을 검증해 내부 필터로 변환한다.
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "날짜 범위가 유효하지 않습니다.");
		}
		Set<PerformanceStatus> statuses = status == null ? EnumSet.allOf(PerformanceStatus.class)
				: parse(status);
		Comparator<Performance> comparator = switch (sort) {
			case "performDate,asc" -> Comparator.comparing(Performance::getPerformDate,
					Comparator.nullsLast(Comparator.naturalOrder()));
			case "performDate,desc" -> Comparator.comparing(Performance::getPerformDate,
					Comparator.nullsLast(Comparator.reverseOrder()));
			case "createdAt,desc" -> Comparator.comparing(Performance::getCreatedAt).reversed();
			default -> throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "sort가 유효하지 않습니다.");
		};
		// 권한으로 제한된 공연 목록에 상태·프리셋·날짜 필터와 정렬을 적용한다.
		List<Performance> selected = performances.list(clubId, actorUserId).stream()
				.filter(item -> statuses.contains(item.getStatus()))
				.filter(item -> preset == null || item.getPreset() == preset)
				.filter(item -> from == null || (item.getPerformDate() != null
						&& !item.getPerformDate().isBefore(from)))
				.filter(item -> to == null || (item.getPerformDate() != null
						&& !item.getPerformDate().isAfter(to)))
				.sorted(comparator.thenComparing(Performance::getId)).toList();
		int start = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), selected.size());
		int end = Math.min(start + pageable.getPageSize(), selected.size());
		// 현재 페이지의 각 공연에 정책과 본인 참여 상태를 결합한다.
		Long membershipId = memberships.requireMember(clubId, actorUserId).getId();
		List<PerformanceListItem> items = selected.subList(start, end).stream().map(performance -> {
			var policy = policies.findById(performance.getId())
					.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연 정책을 찾을 수 없습니다."));
			var participation = participants.findByPerformance_IdAndMembership_Id(
					performance.getId(), membershipId).map(member -> member.getStatus()).orElse(null);
			return PerformanceListItem.from(performance, policy, participation);
		}).toList();
		return new PageImpl<>(items, pageable, selected.size());
	}

	/** 쉼표로 전달된 공연 상태 필터를 검증해 집합으로 변환한다. */
	private static Set<PerformanceStatus> parse(String raw) {
		try {
			EnumSet<PerformanceStatus> result = EnumSet.noneOf(PerformanceStatus.class);
			Arrays.stream(raw.split(",", -1)).forEach(item -> result.add(PerformanceStatus.valueOf(item)));
			if (result.isEmpty()) {
				throw new IllegalArgumentException();
			}
			return result;
		} catch (IllegalArgumentException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 유효하지 않습니다.");
		}
	}
}
