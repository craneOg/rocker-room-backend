package com.crane.bandwebbackend.domain.audition.service;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.dto.AuditionItemResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionListResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreatedItem;
import com.crane.bandwebbackend.domain.audition.dto.MyAuditionItem;
import com.crane.bandwebbackend.domain.audition.dto.MyAuditionsResponse;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.RevisionTag;
import jakarta.persistence.LockModeType;
import com.crane.bandwebbackend.domain.audition.repository.AuditionEntryRepository;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuditionService {
	private final AuditionEntryRepository repository;
	private final ParticipationLookup memberRepository;
	private final MembershipService membershipService;
	private final PerformanceService performanceService;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** API-150: 본인 세션 심사와 본인이 제안한 곡의 심사만 조회한다. */
	@Transactional(readOnly = true)
	public MyAuditionsResponse mine(
		Long actorUserId,
		Long clubId,
		Long performanceId
	) {
		// 본인이 세션 지원자인 심사와 본인이 제안한 곡 심사를 함께 조회한다.
		List<AuditionEntry> owned = new ArrayList<>(
				repository.findByPerformanceMember_Membership_User_Id(actorUserId));
		owned.addAll(repository.findBySong_ProposedBy_User_IdAndPerformanceMemberIsNull(actorUserId));
		// 선택된 동아리·공연만 남기고 곡/파트/지원자 단위의 재심사 이력으로 묶는다.
		Map<String, List<AuditionEntry>> groups = owned.stream()
				.filter(entry -> clubId == null
						|| clubId.equals(entry.getSong().getPerformance().getClub().getId()))
				.filter(entry -> performanceId == null || performanceId.equals(entry.getPerformanceId()))
				.collect(Collectors.groupingBy(entry -> entry.getSong().getId() + ":"
						+ (entry.getPart() == null ? "song" : entry.getPart().getId() + ":"
						+ entry.getPerformanceMember().getId())));
		// 각 이력과 그룹 모두 최신 심사가 먼저 보이도록 정렬 기준을 만든다.
		Comparator<AuditionEntry> newestFirst = Comparator
				.comparing(AuditionEntry::getCreatedAt).reversed()
				.thenComparing(AuditionEntry::getId, Comparator.reverseOrder());
		List<List<AuditionEntry>> sortedGroups = groups.values().stream()
				.peek(group -> group.sort(newestFirst))
				.sorted((left, right) -> newestFirst.compare(left.get(0), right.get(0)))
				.toList();
		List<MyAuditionItem> items = new ArrayList<>();
		// 그룹의 첫 항목만 최신 심사로 표시하면서 평탄한 응답 목록으로 변환한다.
		for (List<AuditionEntry> group : sortedGroups) {
			for (int i = 0; i < group.size(); i++) {
				items.add(MyAuditionItem.from(group.get(i), i == 0));
			}
		}
		return MyAuditionsResponse.builder().items(items).build();
	}

	/** API-148: 곡 행을 잠그고 중복·자격을 전수 확인한 뒤 일괄 생성한다. */
	@Transactional
	public List<AuditionCreatedItem> create(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		AuditionCreateRequest body
	) {
		// 임원 권한과 공연·동아리 상태를 확인해 심사 등록 가능 범위를 확정한다.
		membershipService.requireAdmin(clubId, actorUserId);
		var performance = performanceService.require(clubId, performanceId);
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 등록할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		if (performance.getStatus() == PerformanceStatus.FINISHED
				|| performance.getStatus() == PerformanceStatus.CANCELED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "공연 상태가 등록을 허용하지 않습니다.",
					"INVALID_STATE");
		}
		// 요청 유형을 곡 자체 심사와 특정 세션 지원자 심사로 구분한다.
		boolean session = "SESSION".equals(body.type());
		if (!session && !"SONG".equals(body.type())) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "오디션 유형이 유효하지 않습니다.");
		}
		// 동시 중복 등록을 막기 위해 대상 곡을 쓰기 잠금으로 조회한다.
		Long songId = ApiIds.positive(body.songId(), "songId");
		Song song = entityManager.find(Song.class, songId, LockModeType.PESSIMISTIC_WRITE);
		if (song == null || !song.getPerformance().getId().equals(performanceId)) {
			throw BusinessException.of(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
		}
		if (song.getStatus() != SongStatus.PROPOSED && song.getStatus() != SongStatus.CONFIRMED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "심사 대상 곡이 아닙니다.",
					"SONG_NOT_AUDITIONABLE");
		}
		// 일정이 연결됐다면 같은 공연의 오디션 유형 일정인지 검증한다.
		PracticeSchedule schedule = null;
		if (body.linkedScheduleId() != null) {
			schedule = entityManager.find(PracticeSchedule.class,
					ApiIds.positive(body.linkedScheduleId(), "linkedScheduleId"));
			if (schedule == null || !schedule.getPerformance().getId().equals(performanceId)
					|| schedule.getScheduleType() != ScheduleType.AUDITION) {
				throw BusinessException.of(ErrorCode.RULE_VIOLATION, "오디션 일정이 아닙니다.",
						"SCHEDULE_NOT_AUDITION");
			}
		}
		// 세션 심사는 활성 파트와 1~50명의 중복 없는 지원자가 반드시 필요하다.
		Part part = null;
		List<Long> applicantIds = new ArrayList<>();
		if (session) {
			if (body.partId() == null || body.applicantPerformanceMemberIds() == null
					|| body.applicantPerformanceMemberIds().isEmpty()
					|| body.applicantPerformanceMemberIds().size() > 50) {
				throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "세션 심사에는 파트와 지원자가 필요합니다.");
			}
			part = entityManager.find(Part.class, ApiIds.positive(body.partId(), "partId"));
			if (part == null || !part.getClub().getId().equals(clubId) || !part.isActive()) {
				throw BusinessException.of(ErrorCode.RULE_VIOLATION, "활성 파트가 아닙니다.",
						"PART_NOT_ACTIVE");
			}
			Set<Long> uniqueIds = new HashSet<>();
			for (String raw : body.applicantPerformanceMemberIds()) {
				Long id = ApiIds.positive(raw, "applicantPerformanceMemberIds");
				if (!uniqueIds.add(id)) {
					throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "중복 지원자가 있습니다.");
				}
				applicantIds.add(id);
			}
		} else if (body.partId() != null || body.applicantPerformanceMemberIds() != null
				&& !body.applicantPerformanceMemberIds().isEmpty()) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "곡 심사에는 지원자와 파트를 지정할 수 없습니다.");
		}
		// 모든 지원자가 같은 공연에서 지원 또는 승인 상태인지 전수 확인한다.
		List<PerformanceMember> applicants = new ArrayList<>();
		for (Long id : applicantIds) {
			PerformanceMember applicant = memberRepository.member(clubId, performanceId, id)
					.orElseThrow(() -> BusinessException.of(ErrorCode.RULE_VIOLATION,
					"지원자 자격이 없습니다.", "APPLICANT_NOT_ELIGIBLE"));
			if (applicant.getStatus() != PerformanceMemberStatus.APPLIED
					&& applicant.getStatus() != PerformanceMemberStatus.APPROVED) {
				throw BusinessException.of(ErrorCode.RULE_VIOLATION, "지원자 자격이 없습니다.",
						"APPLICANT_NOT_ELIGIBLE");
			}
			applicants.add(applicant);
		}
		// 같은 곡·파트·지원자의 미심사 기록이 있으면 중복 심사를 만들지 않는다.
		Long partId = part == null ? null : part.getId();
		List<AuditionEntry> pending = repository.findByPerformanceId(performanceId).stream()
				.filter(entry -> entry.getResult() == null && entry.getSong().getId().equals(songId))
				.filter(entry -> session ? entry.getPart() != null
						&& entry.getPart().getId().equals(partId)
						&& applicants.stream().anyMatch(applicant -> entry.getPerformanceMember() != null
						&& entry.getPerformanceMember().getId().equals(applicant.getId()))
						: entry.getPerformanceMember() == null)
				.toList();
		if (!pending.isEmpty()) {
			throw BusinessException.of(ErrorCode.DUPLICATE, "심사 대기 기록이 이미 있습니다.",
					"AUDITION_ALREADY_PENDING");
		}
		// 곡 심사는 한 건, 세션 심사는 지원자마다 한 건씩 생성한다.
		User creator = entityManager.getReference(User.class, actorUserId);
		List<AuditionEntry> created = new ArrayList<>();
		if (!session) {
			created.add(AuditionEntry.create(performanceId, song, null, null, schedule,
					body.memo(), creator));
		} else {
			for (PerformanceMember applicant : applicants) {
				created.add(AuditionEntry.create(performanceId, song, applicant, part, schedule,
						body.memo(), creator));
			}
		}
		// 생성 결과를 먼저 확정해 ID를 얻고 각 심사별 감사 로그를 남긴다.
		repository.saveAllAndFlush(created);
		for (AuditionEntry entry : created) {
			auditRecorder.record(clubId, actorUserId, "AUDITION_CREATED", "AUDITION", entry.getId(), null, Map.of("songId", songId));
		}
		return created.stream().map(AuditionCreatedItem::from).toList();
	}

	/** 오디션 심사 결과를 입력하거나 정정한다 */
	@Transactional
	public AuditionReviewResponse review(
		Long clubId,
		Long performanceId,
		Long auditionId,
		Long actorUserId,
		AuditionReviewRequest body
	) {
		// 임원 권한과 공연 상태를 확인하고, 잠긴 공연의 예외 수정에는 사유를 요구한다.
		membershipService.requireAdmin(clubId, actorUserId);
		var performance = performanceService.require(clubId, performanceId);
		if (performance.getStatus() == PerformanceStatus.FINISHED
				|| performance.getStatus() == PerformanceStatus.CANCELED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "이 공연에서는 심사할 수 없습니다.",
					"INVALID_STATE");
		}
		if (performance.getStatus() == PerformanceStatus.LOCKED
				&& (body.reason() == null || body.reason().isBlank())) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유가 필요합니다.", "REASON_REQUIRED");
		}
		// 수정 행을 잠근 뒤 현재 revision을 다시 계산해 동시 수정 충돌을 검사한다.
		AuditionEntry entry = repository.findWithLockByIdAndPerformanceId(auditionId, performanceId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "오디션을 찾을 수 없습니다."));
		if (!RevisionTag.from("a", entry.getId(), entry.getUpdatedAt()).equals(body.revision())) {
			throw BusinessException.of(ErrorCode.VERSION_CONFLICT, "오디션이 먼저 변경되었습니다.");
		}
		// 이전 결과를 보존한 뒤 심사 결과·의견·심사자·시각을 갱신한다.
		var previous = entry.getResult();
		User reviewer = entityManager.find(User.class, actorUserId);
		entry.review(body.result(), body.comment(), reviewer, LocalDateTime.now(ZoneOffset.UTC));
		Map<String, Object> detail = new java.util.HashMap<>();
		detail.put("from", previous == null ? null : previous.toString());
		detail.put("to", body.result().toString());
		// 일반 심사 감사 로그와 잠금 후 예외 수정 로그를 필요에 따라 함께 남긴다.
		auditRecorder.record(clubId, actorUserId, "AUDITION_REVIEWED", "AUDITION", auditionId, body.reason(), detail);
		if (performance.getStatus() == PerformanceStatus.LOCKED) {
			auditRecorder.record(clubId, actorUserId, "LOCKED_EXCEPTION_EDIT", "AUDITION", auditionId, body.reason(), detail);
		}
		entityManager.flush();
		entityManager.refresh(entry);
		return AuditionReviewResponse.from(entry);
	}

	/** API-147: tab과 무관한 pendingCount를 같은 필터 집합에서 계산한다. */
	@Transactional(readOnly = true)
	public AuditionListResponse pageAdmin(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		String tab,
		String type,
		Long songId,
		Long scheduleId,
		int page,
		int size
	) {
		// 임원 전용 목록이므로 권한·공연 경계와 페이지/필터 값을 먼저 검증한다.
		membershipService.requireAdmin(clubId, actorUserId);
		performanceService.require(clubId, performanceId);
		if (page < 0 || size < 1 || size > 100) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "page 또는 size 범위가 유효하지 않습니다.");
		}
		if (tab != null && !tab.equals("PENDING") && !tab.equals("DONE")) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "tab 값이 유효하지 않습니다.");
		}
		if (type != null && !type.equals("SONG") && !type.equals("SESSION")) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "type 값이 유효하지 않습니다.");
		}
		// tab을 제외한 유형·곡·일정 필터를 먼저 적용해 전체 대기 건수의 기준을 만든다.
		List<AuditionEntry> filtered = repository.findByPerformanceId(performanceId).stream()
				.filter(entry -> type == null || (entry.getPerformanceMember() == null ? "SONG" : "SESSION")
						.equals(type))
				.filter(entry -> songId == null || songId.equals(entry.getSong().getId()))
				.filter(entry -> scheduleId == null || entry.getPracticeSchedule() != null
						&& scheduleId.equals(entry.getPracticeSchedule().getId()))
				.toList();
		long pendingCount = filtered.stream().filter(entry -> entry.getResult() == null).count();
		// 곡별로 묶이도록 정렬하고, 대기는 오래된 순·완료는 최근 심사 순으로 배치한다.
		Comparator<AuditionEntry> order = Comparator
				.comparing((AuditionEntry entry) -> entry.getSong().getTitle(),
						String.CASE_INSENSITIVE_ORDER)
				.thenComparing(entry -> entry.getSong().getId())
				.thenComparing(entry -> entry.getResult() == null ? 0 : 1)
				.thenComparing((left, right) -> {
					if (left.getResult() == null && right.getResult() == null) {
						return left.getCreatedAt().compareTo(right.getCreatedAt());
					}
					if (left.getResult() != null && right.getResult() != null) {
						return right.getReviewedAt().compareTo(left.getReviewedAt());
					}
					return 0;
				})
				.thenComparing(AuditionEntry::getId);
		// 마지막에 tab을 적용한 뒤 요청 페이지 구간만 응답 DTO로 변환한다.
		List<AuditionEntry> matches = filtered.stream()
				.filter(entry -> tab == null || (tab.equals("PENDING") == (entry.getResult() == null)))
				.sorted(order).toList();
		int from = (int) Math.min(matches.size(), (long) page * size);
		int to = Math.min(matches.size(), from + size);
		return AuditionListResponse.builder()
				.items(matches.subList(from, to).stream().map(AuditionItemResponse::from).toList())
				.page(page).size(size).totalElements(matches.size())
				.totalPages((matches.size() + size - 1) / size)
				.pendingCount(pendingCount).build();
	}

	/** 오디션 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<AuditionEntry> list(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 동아리 회원 여부와 공연 경계를 확인한 뒤 전체 심사 목록을 읽는다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		performanceService.require(clubId, performanceId);
		List<AuditionEntry> entries = repository.findByPerformanceId(performanceId);
		// 임원은 전체를 보고, 일반 회원은 본인이 지원자인 세션 심사만 본다.
		if (actor.getRole() == MembershipRole.CLUB_ADMIN) {
			return entries;
		}
		PerformanceMember member = memberRepository.memberOfMembership(performanceId, actor.getId())
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "오디션 목록을 찾을 수 없습니다."));
		return entries.stream().filter(entry -> entry.getPerformanceMember() != null
				&& entry.getPerformanceMember().getId().equals(member.getId())).toList();
	}

	/** 오디션 상세 정보를 조회한다 */
	@Transactional(readOnly = true)
	public AuditionEntry detail(
		Long clubId,
		Long performanceId,
		Long auditionId,
		Long actorUserId
	) {
		// 동아리 회원 여부와 URL의 공연·심사 소속 관계를 검증한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		performanceService.require(clubId, performanceId);
		AuditionEntry entry = repository.findByIdAndPerformanceId(auditionId, performanceId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "오디션을 찾을 수 없습니다."));
		// 임원 외에는 해당 심사의 지원자 본인에게만 상세 내용을 공개한다.
		if (actor.getRole() == MembershipRole.CLUB_ADMIN) {
			return entry;
		}
		if (entry.getPerformanceMember() == null
				|| !entry.getPerformanceMember().getMembership().getId().equals(actor.getId())) {
			throw BusinessException.of(ErrorCode.NOT_FOUND, "오디션을 찾을 수 없습니다.");
		}
		return entry;
	}
}
