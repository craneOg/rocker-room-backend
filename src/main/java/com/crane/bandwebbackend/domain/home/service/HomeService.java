package com.crane.bandwebbackend.domain.home.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.home.dto.HomeBadgeResponse;
import com.crane.bandwebbackend.domain.home.dto.HomeResponse;
import com.crane.bandwebbackend.domain.home.repository.HomeQueryRepository;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.dto.CalendarEvent;
import com.crane.bandwebbackend.domain.schedule.service.CalendarReader;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 137·139: 부원 홈(읽기 전용)
@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int SCHEDULE_DAYS = 93;
    private static final int SCHEDULE_LIMIT = 3;

    private final MembershipService membershipService;
    private final HomeAdminService homeAdminService;
    private final PerformanceService performanceService;
    private final CalendarReader calendarReader;
    private final HomeQueryRepository repository;

    @Transactional(readOnly = true)
    public HomeResponse home(Long clubId, Long userId) {
        Membership m = membershipService.requireMember(clubId, userId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // 객원·초안 가시성은 PerformanceService.list가 처리한다(G-06)
        List<Performance> visible = performanceService.list(clubId, userId);
        Map<Long, Performance> byId = byId(visible);
        Map<Long, HomeQueryRepository.TodoRow> rows = rows(m, visible);

        List<HomeResponse.Banner> banners = new ArrayList<>();
        if (inactive(m)) {
            banners.add(HomeResponse.Banner.builder().type("CLUB_INACTIVE").build());
        }
        if (m.getStatus() == MembershipStatus.GRADUATED) {
            banners.add(HomeResponse.Banner.builder().type("MEMBER_GRADUATED").build());
        }
        if (isAdmin(m)) {
            LocalDate today = LocalDate.now(KstTime.KST);
            visible.stream()
                    .filter(p -> p.getStatus() == PerformanceStatus.LOCKED
                            && p.getPerformDate() != null
                            && p.getPerformDate().isBefore(today))
                    .sorted(Comparator.comparing(Performance::getId))
                    .forEach(p -> banners.add(HomeResponse.Banner.builder()
                            .type("PERFORMANCE_FINISH_DUE")
                            .performanceId(p.getId())
                            .performanceName(p.getTitle())
                            .performanceDate(p.getPerformDate())
                            .build()));
        }

        List<Long> myIds = rows.values().stream()
                .filter(r -> isMine(r) && !"DRAFT".equals(r.getStatus()))
                .map(HomeQueryRepository.TodoRow::getPerformanceId)
                .toList();
        Map<Long, List<String>> teamNames = repository.approvedTeamNames(m.getId(), myIds);
        List<HomeResponse.MyPerformance> mine = visible.stream()
                .filter(p -> myIds.contains(p.getId()))
                .sorted(Comparator.comparing(
                                Performance::getPerformDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Performance::getId))
                .map(p -> HomeResponse.MyPerformance.builder()
                        .performanceId(p.getId())
                        .name(p.getTitle())
                        .status(p.getStatus().name())
                        .performanceDate(p.getPerformDate())
                        .myStatus(rows.get(p.getId()).getPmStatus())
                        // APPROVED 팀만 보여 준다
                        .teamNames(teamNames.getOrDefault(p.getId(), List.of()))
                        .songCount(rows.get(p.getId()).getSongCount())
                        .build())
                .toList();

        return HomeResponse.builder()
                .banners(banners)
                .todos(todos(m, byId, rows, now))
                .upcomingSchedules(schedules(clubId, userId, now, byId))
                .myPerformances(mine)
                .build();
    }

    @Transactional(readOnly = true)
    public HomeBadgeResponse badge(Long clubId, Long userId) {
        Membership m = membershipService.requireMember(clubId, userId);
        List<Performance> visible = performanceService.list(clubId, userId);
        long count = todos(m, byId(visible), rows(m, visible), LocalDateTime.now(ZoneOffset.UTC))
                .size();
        if (isAdmin(m)) {
            count += homeAdminService.pending(clubId).total();
        }
        return HomeBadgeResponse.builder().count(count).build();
    }

    private static Map<Long, Performance> byId(List<Performance> visible) {
        return visible.stream().collect(Collectors.toMap(Performance::getId, Function.identity()));
    }

    // 진행 중 공연(DRAFT·ACTIVE·LOCKED)별 내 판정 재료
    private Map<Long, HomeQueryRepository.TodoRow> rows(Membership m, List<Performance> visible) {
        List<Long> ids = visible.stream()
                .filter(p -> p.getStatus() == PerformanceStatus.DRAFT
                        || p.getStatus() == PerformanceStatus.ACTIVE
                        || p.getStatus() == PerformanceStatus.LOCKED)
                .map(Performance::getId)
                .toList();
        return repository.todoRows(m.getId(), m.getMemberType() == MemberType.GUEST, ids).stream()
                .collect(Collectors.toMap(HomeQueryRepository.TodoRow::getPerformanceId, Function.identity()));
    }

    // 할 일 판정(G-10). 기능 "열림"은 PerformanceAccess.requireOpen과 같은 식이고, 비활성 동아리(G-08)는 할 일이 없다
    private List<HomeResponse.Todo> todos(
            Membership m, Map<Long, Performance> byId, Map<Long, HomeQueryRepository.TodoRow> rows, LocalDateTime now) {
        List<HomeResponse.Todo> out = new ArrayList<>();
        if (inactive(m)) {
            return out;
        }
        boolean regular = m.getMemberType() == MemberType.REGULAR
                && (m.getStatus() == MembershipStatus.ACTIVE || m.getStatus() == MembershipStatus.ON_LEAVE);
        for (HomeQueryRepository.TodoRow r : rows.values()) {
            Performance p = byId.get(r.getPerformanceId());
            boolean active = "ACTIVE".equals(r.getStatus());
            boolean approved = "APPROVED".equals(r.getPmStatus());
            boolean participant = approved || "APPLIED".equals(r.getPmStatus());
            // PerformanceMemberService.apply: ACTIVE 공연, 신청 열림, 정회원. 참여 행이 없거나 WITHDRAWN일 때만(REJECTED 제외)
            if (active
                    && regular
                    && r.getApplication().open(now)
                    && (r.getPmStatus() == null || "WITHDRAWN".equals(r.getPmStatus()))) {
                add(out, p, "PARTICIPATION_APPLY", "공연 참여 신청", null, r.getApplication());
            }
            // S-03: LOCKED 전까지(DRAFT·ACTIVE), 참여자, 팀 범위면 팀원. 한도 null이면 1곡 제안 시 완료
            Integer limit = r.getCandidateLimit();
            if (!"LOCKED".equals(r.getStatus())
                    && participant
                    && r.getCandidate().open(now)
                    && (!"TEAM_MEMBERS".equals(r.getCandidateScope()) || r.isInTeam())
                    && r.getMyProposals() < (limit == null ? 1 : limit)) {
                String label = limit == null ? "후보곡 제안" : "후보곡 제안 (" + r.getMyProposals() + "/" + limit + "곡)";
                add(out, p, "SONG_PROPOSE", label, null, r.getCandidate());
            }
            // 가불: PROPOSED 곡 중 응답할 수 있는 곡에 모두 응답해야 완료
            if (active && participant && r.getPlayability().open(now) && r.getUnanswered() > 0) {
                add(out, p, "PLAYABILITY", "가불 응답 (" + r.getUnanswered() + "곡 남음)", null, r.getPlayability());
            }
            // V-05: 찬성 방식의 한도가 있을 때만 한도까지(투표할 수 있는 곡 수가 한도보다 적으면 그 수까지), 아니면 1건 투표 시 완료
            Integer max = r.getVoteMaxChoices();
            boolean limited = max != null && "APPROVAL".equals(r.getVoteMethod());
            long target = limited ? Math.min(max, r.getVotable()) : 1;
            if (active && approved && r.getVote().open(now) && r.getVotable() > 0 && r.getMyVotes() < target) {
                String label = limited ? "투표 (선택 " + r.getMyVotes() + "/" + target + "곡)" : "투표";
                add(out, p, "VOTE", label, null, r.getVote());
            }
            // AV-01: 가능 시간을 제출한 적이 없으면 미입력(빈 목록 저장은 제출)
            if ((active || "LOCKED".equals(r.getStatus()))
                    && approved
                    && r.getAvailability().open(now)
                    && !r.isAvailabilitySubmitted()) {
                add(out, p, "AVAILABILITY", "합주 가능 시간 입력", null, r.getAvailability());
            }
        }
        // T-02: 팀 제출이 열려 있는 ACTIVE 공연에서 내가 팀장인 DRAFT 팀
        List<Long> teamIds = rows.values().stream()
                .filter(r ->
                        "ACTIVE".equals(r.getStatus()) && r.getTeamSubmission().open(now))
                .map(HomeQueryRepository.TodoRow::getPerformanceId)
                .toList();
        for (HomeQueryRepository.LedTeam t : repository.ledDraftTeams(m.getId(), teamIds)) {
            add(
                    out,
                    byId.get(t.getPerformanceId()),
                    "TEAM_SUBMIT",
                    "작성 중인 팀 신청서",
                    t.getTeamId(),
                    rows.get(t.getPerformanceId()).getTeamSubmission());
        }
        // 마감 임박 순, 마감 없는 항목은 뒤
        out.sort(Comparator.comparing(HomeResponse.Todo::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(HomeResponse.Todo::getPerformanceId));
        return out;
    }

    private static void add(
            List<HomeResponse.Todo> out,
            Performance p,
            String type,
            String label,
            Long teamId,
            HomeQueryRepository.Feature feature) {
        out.add(HomeResponse.Todo.builder()
                .type(type)
                .performanceId(p.getId())
                .performanceName(p.getTitle())
                .label(label)
                .teamId(teamId)
                .deadline(KstTime.toKst(feature.getDeadline()))
                .build());
    }

    // 내가 참석자인 CONFIRMED 일정 중 시작 순 3개
    private List<HomeResponse.UpcomingSchedule> schedules(
            Long clubId, Long userId, LocalDateTime now, Map<Long, Performance> byId) {
        List<CalendarEvent> events =
                calendarReader
                        .personal(userId, now, now.plusDays(SCHEDULE_DAYS), String.valueOf(clubId), false, false)
                        .stream()
                        .limit(SCHEDULE_LIMIT)
                        .toList();
        Map<Long, String> locations = repository.scheduleLocations(
                events.stream().map(e -> Long.valueOf(e.getId())).toList());
        return events.stream()
                .map(e -> HomeResponse.UpcomingSchedule.builder()
                        .scheduleId(Long.valueOf(e.getId()))
                        .performanceId(Long.valueOf(e.getPerformanceId()))
                        .startAt(e.getStartAt())
                        .endAt(e.getEndAt())
                        .type(e.getEventType())
                        .title(e.getTitle())
                        .performanceName(
                                byId.get(Long.valueOf(e.getPerformanceId())).getTitle())
                        .location(locations.get(Long.valueOf(e.getId())))
                        .build())
                .toList();
    }

    private static boolean isMine(HomeQueryRepository.TodoRow r) {
        return "APPLIED".equals(r.getPmStatus()) || "APPROVED".equals(r.getPmStatus());
    }

    private static boolean inactive(Membership m) {
        return m.getClub().getStatus() == ClubStatus.INACTIVE;
    }

    // requireAdmin과 같은 임원 조건
    private static boolean isAdmin(Membership m) {
        return m.getRole() == MembershipRole.CLUB_ADMIN
                && m.getMemberType() == MemberType.REGULAR
                && (m.getStatus() == MembershipStatus.ACTIVE || m.getStatus() == MembershipStatus.ON_LEAVE);
    }
}
