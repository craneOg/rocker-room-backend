package com.crane.bandwebbackend.domain.home.service;

import com.crane.bandwebbackend.domain.home.dto.ClubDashboardResponse;
import com.crane.bandwebbackend.domain.home.dto.HomeAdminResponse;
import com.crane.bandwebbackend.domain.home.repository.HomeQueryRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.LockChecklistItem;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator;
import com.crane.bandwebbackend.domain.performance.service.LockReadinessReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 117·138: 임원용 운영 현황 집계(읽기 전용)
@Service
@RequiredArgsConstructor
public class HomeAdminService {

    private final MembershipService membershipService;
    private final HomeQueryRepository repository;
    private final LockReadinessReader lockReadinessReader;

    @Transactional(readOnly = true)
    public ClubDashboardResponse dashboard(Long clubId, Long userId) {
        membershipService.requireAdmin(clubId, userId);
        HomeQueryRepository.MemberCounts members = repository.memberCounts(clubId, utcNow());
        HomeQueryRepository.WorkCounts work = repository.workCounts(clubId);
        // 충족되지 않은 검사 항목(BLOCKER·WARNING 모두) 수의 합
        long warnings = performanceChecks(clubId).stream()
                .mapToLong(c -> c.getTotal() - c.getSatisfied())
                .sum();
        Map<String, Long> byStatus = new TreeMap<>(repository.performanceStatusCounts(clubId));
        return ClubDashboardResponse.builder()
                .pendingMemberships(members.getPending())
                .invitedMemberships(members.getInvited())
                .activeMembers(members.getActive())
                .performancesByStatus(byStatus)
                .pendingTeams(work.getSubmittedTeams())
                .pendingPerformanceMembers(work.getAppliedMembers())
                .warningsCount(warnings)
                .build();
    }

    @Transactional(readOnly = true)
    public HomeAdminResponse adminHome(Long clubId, Long userId) {
        membershipService.requireAdmin(clubId, userId);
        HomeQueryRepository.MemberCounts members = repository.memberCounts(clubId, utcNow());
        HomeQueryRepository.WorkCounts work = repository.workCounts(clubId);
        Map<String, Long> status = repository.performanceStatusCounts(clubId);
        return HomeAdminResponse.builder()
                .pending(pending(members, work))
                .performanceChecks(performanceChecks(clubId))
                .clubStats(HomeAdminResponse.ClubStats.builder()
                        .activeMembers(members.getLive())
                        .admins(members.getAdmins())
                        .graduated(members.getGraduated())
                        .guests(members.getGuests())
                        .performances(HomeAdminResponse.Performances.builder()
                                .draft(status.getOrDefault("DRAFT", 0L))
                                .active(status.getOrDefault("ACTIVE", 0L))
                                .locked(status.getOrDefault("LOCKED", 0L))
                                .finished(status.getOrDefault("FINISHED", 0L))
                                .build())
                        .build())
                .build();
    }

    // API 139가 임원 합계를 구할 때도 쓴다(권한은 호출자가 확인)
    @Transactional(readOnly = true)
    public HomeAdminResponse.Pending pending(Long clubId) {
        return pending(repository.memberCounts(clubId, utcNow()), repository.workCounts(clubId));
    }

    private static HomeAdminResponse.Pending pending(
            HomeQueryRepository.MemberCounts members, HomeQueryRepository.WorkCounts work) {
        return HomeAdminResponse.Pending.builder()
                .joinRequests(members.getPending())
                .invitesWaiting(members.getInvited())
                .participationRequests(work.getAppliedMembers())
                .teamReviews(work.getSubmittedTeams())
                .auditionReviews(work.getPendingAuditions())
                .build();
    }

    // ACTIVE 공연마다 052와 같은 원본(LockReadinessReader + LockChecklistEvaluator)으로 점검하고 응답률을 붙인다
    private List<HomeAdminResponse.PerformanceCheck> performanceChecks(Long clubId) {
        LocalDateTime now = utcNow();
        List<HomeAdminResponse.PerformanceCheck> out = new ArrayList<>();
        for (HomeQueryRepository.RateRow row : repository.rateRows(clubId)) {
            // 평가기는 위반 대상마다 한 줄을 돌려주므로 checkId로 묶는다(충족 시 SATISFIED 한 줄)
            Map<String, Long> violations = new LinkedHashMap<>();
            for (LockChecklistItem item : LockChecklistEvaluator.evaluate(
                            lockReadinessReader.read(row.getPerformanceId()))
                    .getItems()) {
                violations.merge(
                        item.getCheckId(), item.getResult() == LockChecklistItem.Result.SATISFIED ? 0L : 1L, Long::sum);
            }
            List<HomeAdminResponse.CheckItem> items = violations.entrySet().stream()
                    .map(e -> HomeAdminResponse.CheckItem.builder()
                            .checkId(e.getKey())
                            .kind(e.getKey().compareTo("L-06") <= 0 ? "BLOCKER" : "WARNING")
                            .satisfied(e.getValue() == 0)
                            .count(e.getValue())
                            .build())
                    .toList();
            out.add(HomeAdminResponse.PerformanceCheck.builder()
                    .performanceId(row.getPerformanceId())
                    .performanceName(row.getTitle())
                    .satisfied((int) items.stream()
                            .filter(HomeAdminResponse.CheckItem::isSatisfied)
                            .count())
                    .total(items.size())
                    .items(items)
                    .responseRates(HomeAdminResponse.ResponseRates.builder()
                            .playability(
                                    row.getPlayability().open(now)
                                            ? rate(row.getPlayabilityAnswered(), row.getPlayabilityTotal())
                                            : null)
                            .vote(row.getVote().open(now) ? rate(row.getVoteAnswered(), row.getApprovedTotal()) : null)
                            .availability(
                                    row.getAvailability().open(now)
                                            ? rate(row.getAvailabilityAnswered(), row.getApprovedTotal())
                                            : null)
                            .build())
                    .build());
        }
        return out;
    }

    private static HomeAdminResponse.Rate rate(long answered, long total) {
        return HomeAdminResponse.Rate.builder().answered(answered).total(total).build();
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
