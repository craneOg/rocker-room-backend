package com.crane.bandwebbackend.domain.availability.service;

import com.crane.bandwebbackend.domain.availability.dto.AvailabilityRequest;
import com.crane.bandwebbackend.domain.availability.dto.AvailabilityResponse;
import com.crane.bandwebbackend.domain.availability.dto.CommonAvailabilityResponse;
import com.crane.bandwebbackend.domain.availability.entity.AvailableSlot;
import com.crane.bandwebbackend.domain.availability.repository.AvailableSlotRepository;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 합주 가능 시간(API-100~103). 저장 단위는 30분 슬롯 1행(available_slot). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private static final Duration SLOT = Duration.ofMinutes(30);
    // 100·101·103 한 요청 기간 상한(#30). 31일 × 48칸 = 1488칸이라 슬롯 상한 2000 안에 들어감
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final AvailableSlotRepository slotRepository;
    private final PerformanceAccessService accessService;
    private final TeamService teamService;
    private final SongSupport songSupport;

    // API-100. 더 이상 참여자가 아니어도 본인 기록은 읽을 수 있음. 마감 후에도 조회 가능
    public AvailabilityResponse getMine(PerformanceRef ref, OffsetDateTime from, OffsetDateTime to) {
        PerformanceAccess access = accessService.get(ref);
        PerformanceMember pm = access.getPerformanceMember();
        if (pm == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "공연 참여자만 조회할 수 있습니다.");
        }
        return read(pm.getId(), from, to);
    }

    // API-102 임원의 원본 조회. 대리 쓰기 API는 없음
    public AvailabilityResponse getMember(
            PerformanceRef ref, Long performanceMemberId, OffsetDateTime from, OffsetDateTime to) {
        PerformanceAccess access = accessService.get(ref);
        access.requireAdmin();
        return read(
                accessService
                        .getMember(access.performanceId(), performanceMemberId)
                        .getId(),
                from,
                to);
    }

    /**
     * API-101. APPROVED 참여자 본인만(AV-01, 파트 무관). 공연 ACTIVE·LOCKED, availabilityState OPEN, 마감 전.
     * 한 요청 = 한 트랜잭션(전체 성공 또는 전체 실패). 참여자 행을 잠근 뒤 처리
     */
    @Transactional
    public AvailabilityResponse replaceMine(PerformanceRef ref, AvailabilityRequest req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        PerformanceMember pm = access.requireApprovedParticipant();
        access.requireStatus(PerformanceStatus.ACTIVE, PerformanceStatus.LOCKED);
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        PerformanceAccess.requireOpen(
                policy.getAvailabilityState(), policy.getAvailabilityDeadline(), "AVAILABILITY_CLOSED");

        List<LocalDateTime> slots = toUtcSlots(
                req.getFrom(),
                req.getTo(),
                req.getSlots().stream()
                        .map(s -> new OffsetDateTime[] {s.getStartAt(), s.getEndAt()})
                        .toList(),
                policy.getPracticeSeasonStart(),
                policy.getPracticeSeasonEnd());
        accessService.lock(pm);
        slotRepository.deleteInRange(pm.getId(), KstTime.toUtc(req.getFrom()), KstTime.toUtc(req.getTo()));
        slotRepository.saveAll(slots.stream().map(s -> AvailableSlot.of(pm, s)).toList());
        return response(pm.getId(), req.getFrom(), req.getTo(), slots);
    }

    /**
     * API-103 공통 가능 시간. 대상 = teamIds 팀원 ∪ songIds 연주자(APPROVED), forAllParticipants면 APPROVED 전원.
     * 미입력자가 있어도 계산하고, 일정은 차감하지 않음(AV-05). 이름은 A에게만(AV-04).
     * A가 아니면 본인이 팀원인 teamIds만 허용(TARGET_NOT_ALLOWED)
     */
    public CommonAvailabilityResponse common(
            PerformanceRef ref,
            OffsetDateTime from,
            OffsetDateTime to,
            List<Long> teamIds,
            List<Long> songIds,
            boolean forAllParticipants) {
        validateRange(from, to);
        PerformanceAccess access = accessService.get(ref);
        Long pid = access.performanceId();
        List<Long> teams = teamIds == null ? List.of() : teamIds;
        List<Long> songs = songIds == null ? List.of() : songIds;
        // 존재하지 않거나 이 공연 것이 아니면 404 → 그다음 권한
        teams.forEach(id -> teamService.getTeam(pid, id));
        songs.forEach(id -> songSupport.getSong(pid, id));
        Map<Long, Set<Long>> teamMembers = teamService.memberIdsByTeam(pid);
        if (!access.isAdmin()) {
            // 참여자가 아니면 먼저 거부(Set.of().contains(null)은 NPE)
            Long me = access.myMemberId();
            boolean allowed = me != null
                    && songs.isEmpty()
                    && !forAllParticipants
                    && teams.stream()
                            .allMatch(
                                    id -> teamMembers.getOrDefault(id, Set.of()).contains(me));
            if (!allowed) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "TARGET_NOT_ALLOWED", "본인 팀만 조회할 수 있습니다.");
            }
        }
        if (teams.isEmpty() && songs.isEmpty() && !forAllParticipants) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "TARGET_REQUIRED", "대상 팀·곡을 선택해 주세요.");
        }

        List<PerformanceMember> approved = accessService.getMembers(pid, List.of(PerformanceMemberStatus.APPROVED));
        Set<Long> targetIds = new HashSet<>();
        if (forAllParticipants) {
            approved.forEach(pm -> targetIds.add(pm.getId()));
        } else {
            teams.forEach(id -> targetIds.addAll(teamMembers.getOrDefault(id, Set.of())));
            Set<Long> songSet = Set.copyOf(songs);
            songSupport.sessionsOfPerformance(pid).stream()
                    .filter(s -> songSet.contains(s.getSong().getId()))
                    .forEach(s -> targetIds.add(s.getPerformanceMember().getId()));
        }
        List<PerformanceMember> targets =
                approved.stream().filter(pm -> targetIds.contains(pm.getId())).toList();
        Map<Long, CommonAvailabilityResponse.Member> names = targets.stream()
                .collect(Collectors.toMap(
                        PerformanceMember::getId,
                        pm -> CommonAvailabilityResponse.Member.builder()
                                .performanceMemberId(pm.getId())
                                .displayName(PerformanceAccessService.displayName(pm))
                                .build()));
        Set<Long> ids = names.keySet();
        Set<Long> responded = ids.isEmpty() ? Set.of() : Set.copyOf(slotRepository.findRespondedMemberIds(ids));
        List<AvailableSlot> slots = ids.isEmpty()
                ? List.of()
                : slotRepository.findByMembersInRange(ids, KstTime.toUtc(from), KstTime.toUtc(to));
        Map<LocalDateTime, List<Long>> bySlot = new TreeMap<>();
        slots.forEach(s -> bySlot.computeIfAbsent(s.getSlotStart(), k -> new ArrayList<>())
                .add(s.getPerformanceMember().getId()));

        boolean admin = access.isAdmin();
        return CommonAvailabilityResponse.builder()
                .from(from.withOffsetSameInstant(KstTime.KST))
                .to(to.withOffsetSameInstant(KstTime.KST))
                .participantCount(targets.size())
                .respondedCount(responded.size())
                .slots(bySlot.entrySet().stream()
                        .map(e -> CommonAvailabilityResponse.Slot.builder()
                                .startAt(KstTime.toKst(e.getKey()))
                                .availableCount(e.getValue().size())
                                .availableMembers(
                                        admin
                                                ? e.getValue().stream()
                                                        .map(names::get)
                                                        .toList()
                                                : null)
                                .build())
                        .toList())
                .participants(
                        admin
                                ? targets.stream()
                                        .map(pm -> names.get(pm.getId()))
                                        .toList()
                                : null)
                .missingAvailabilityMemberIds(
                        admin
                                ? targets.stream()
                                        .map(PerformanceMember::getId)
                                        .filter(id -> !responded.contains(id))
                                        .toList()
                                : null)
                .build();
    }

    private AvailabilityResponse read(Long memberId, OffsetDateTime from, OffsetDateTime to) {
        if ((from == null) != (to == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_RANGE", "from과 to는 함께 보내야 합니다.");
        }
        if (from == null) {
            return response(memberId, null, null, slotRepository.findStarts(memberId));
        }
        validateRange(from, to);
        return response(
                memberId, from, to, slotRepository.findStartsInRange(memberId, KstTime.toUtc(from), KstTime.toUtc(to)));
    }

    /**
     * 요청 구간을 30분 슬롯 시작 시각(UTC)으로 펼침(중복 제거·정렬).
     * AV-02 시작·종료 30분 단위, AV-03 [from, to) 안, 합주 시즌(정책, KST 날짜) 안이 아니면 OUTSIDE_PRACTICE_SEASON
     */
    static List<LocalDateTime> toUtcSlots(
            OffsetDateTime from,
            OffsetDateTime to,
            List<OffsetDateTime[]> intervals,
            LocalDate seasonStart,
            LocalDate seasonEnd) {
        validateRange(from, to);
        TreeSet<LocalDateTime> result = new TreeSet<>();
        for (OffsetDateTime[] interval : intervals) {
            OffsetDateTime start = interval[0];
            OffsetDateTime end = interval[1];
            requireAligned(start);
            requireAligned(end);
            if (!start.isBefore(end)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_SLOT", "시작이 종료보다 앞서야 합니다.");
            }
            if (start.isBefore(from) || end.isAfter(to)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OUTSIDE_RANGE", "요청 기간 안의 시간만 저장할 수 있습니다.");
            }
            for (OffsetDateTime t = start; t.isBefore(end); t = t.plus(SLOT)) {
                LocalDate date = t.atZoneSameInstant(KstTime.KST).toLocalDate();
                if ((seasonStart != null && date.isBefore(seasonStart))
                        || (seasonEnd != null && date.isAfter(seasonEnd))) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR, "OUTSIDE_PRACTICE_SEASON", "합주 시즌 밖의 시간은 입력할 수 없습니다.");
                }
                result.add(KstTime.toUtc(t));
            }
        }
        return List.copyOf(result);
    }

    // 이어지는 슬롯을 [startAt, endAt) 구간으로 합침
    static List<AvailabilityResponse.Interval> merge(List<LocalDateTime> utcStarts) {
        List<AvailabilityResponse.Interval> result = new ArrayList<>();
        LocalDateTime start = null;
        LocalDateTime end = null;
        for (LocalDateTime s : new LinkedHashSet<>(utcStarts)) {
            if (start != null && s.equals(end)) {
                end = s.plus(SLOT);
                continue;
            }
            if (start != null) {
                result.add(interval(start, end));
            }
            start = s;
            end = s.plus(SLOT);
        }
        if (start != null) {
            result.add(interval(start, end));
        }
        return result;
    }

    private static AvailabilityResponse.Interval interval(LocalDateTime start, LocalDateTime end) {
        return AvailabilityResponse.Interval.builder()
                .startAt(KstTime.toKst(start))
                .endAt(KstTime.toKst(end))
                .build();
    }

    private static AvailabilityResponse response(
            Long memberId, OffsetDateTime from, OffsetDateTime to, List<LocalDateTime> utcStarts) {
        return AvailabilityResponse.builder()
                .performanceMemberId(memberId)
                .from(from == null ? null : from.withOffsetSameInstant(KstTime.KST))
                .to(to == null ? null : to.withOffsetSameInstant(KstTime.KST))
                .slots(merge(utcStarts))
                .build();
    }

    private static void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_RANGE", "from과 to가 필요합니다.");
        }
        requireAligned(from);
        requireAligned(to);
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_RANGE", "기간이 올바르지 않습니다.");
        }
    }

    // AV-02: KST 기준 30분 정렬(KST는 정시 오프셋이라 UTC 정렬과 같음)
    private static void requireAligned(OffsetDateTime t) {
        OffsetDateTime kst = t.withOffsetSameInstant(KstTime.KST);
        if (kst.getMinute() % 30 != 0 || kst.getSecond() != 0 || kst.getNano() != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "SLOT_NOT_ALIGNED", "가능 시간은 30분 단위로만 입력할 수 있습니다.");
        }
    }
}
