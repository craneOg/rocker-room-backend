package com.crane.bandwebbackend.domain.admin.service;

import com.crane.bandwebbackend.domain.admin.dto.AdminOverviewResponse;
import com.crane.bandwebbackend.domain.admin.repository.AdminClubQueryRepository;
import com.crane.bandwebbackend.domain.admin.repository.AdminOverviewQueryRepository;
import com.crane.bandwebbackend.domain.admin.repository.AdminOverviewQueryRepository.Table;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 141: 서비스 전체 지표·추이·가입 현황·주의 대상. 새 테이블 없이 현재 값을 집계한다
@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private static final long INACTIVE_DAYS = 180;
    // 주의 대상 목록 상한. 명세에 상한이 없어 응답 크기를 보호하려고 둔 값
    private static final int ATTENTION_LIMIT = 100;

    private final AdminOverviewQueryRepository stats;
    private final AdminClubQueryRepository clubs;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview(int periodDays) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        LocalDate todayKst = KstTime.toKstDate(now);
        // 기간은 KST 달력 날짜 기준으로 오늘 포함 periodDays일. UTC 시작 시각은 첫 날 KST 0시
        LocalDate firstDay = todayKst.minusDays(periodDays - 1L);
        LocalDateTime startUtc = firstDay.atStartOfDay().minusHours(9);

        Map<LocalDate, Long> users = stats.createdPerKstDay(Table.USERS, startUtc);
        Map<LocalDate, Long> newClubs = stats.createdPerKstDay(Table.CLUBS, startUtc);
        Map<LocalDate, Long> performances = stats.createdPerKstDay(Table.PERFORMANCES, startUtc);
        Map<LocalDate, Long> schedules = stats.createdPerKstDay(Table.SCHEDULES, startUtc);
        List<AdminOverviewResponse.TrendPoint> trend = new ArrayList<>();
        for (LocalDate d = firstDay; !d.isAfter(todayKst); d = d.plusDays(1)) {
            trend.add(AdminOverviewResponse.TrendPoint.builder()
                    .date(d)
                    .newUsers(users.getOrDefault(d, 0L))
                    .newClubs(newClubs.getOrDefault(d, 0L))
                    .newPerformances(performances.getOrDefault(d, 0L))
                    .newSchedules(schedules.getOrDefault(d, 0L))
                    .build());
        }

        LocalDateTime inactiveCutoff = now.minus(Duration.ofDays(INACTIVE_DAYS));
        List<AdminOverviewResponse.Attention> attention = clubs.findAttention(inactiveCutoff, ATTENTION_LIMIT).stream()
                .map(a -> AdminOverviewResponse.Attention.builder()
                        .clubId(a.getClubId())
                        .clubName(a.getClubName())
                        .reason(a.getAdminCount() == 0 ? "NO_ADMIN" : "ADMIN_INACTIVE_180D")
                        .lastAdminActiveAt(KstTime.toKst(a.getAdminLastActiveAt()))
                        .build())
                .toList();

        return AdminOverviewResponse.builder()
                .periodDays(periodDays)
                .metrics(AdminOverviewResponse.Metrics.builder()
                        .activeClubs(stats.activeClubs())
                        .totalUsers(stats.totalUsers())
                        .activeUsers30d(stats.activeUsersSince(now.minusDays(30)))
                        .ongoingPerformances(stats.ongoingPerformances())
                        .build())
                .trend(trend)
                .joinStatus(AdminOverviewResponse.JoinStatus.builder()
                        .requested(stats.requested(startUtc))
                        .approved(stats.approved(startUtc))
                        .rejected(stats.rejected(startUtc))
                        .invited(stats.invited(startUtc))
                        .invitationAccepted(stats.invitationAccepted(startUtc))
                        .invitationExpired(stats.invitationExpired(startUtc, now))
                        .build())
                .attention(attention)
                .build();
    }
}
