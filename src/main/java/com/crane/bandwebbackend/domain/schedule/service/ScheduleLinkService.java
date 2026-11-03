package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곡-일정 연결 조회·해제(곡 상세, S-10).
 * ponytail: schedule 도메인 service가 생기면 그쪽으로 옮김(지금은 JPQL 직접 조회)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleLinkService {

    private final EntityManager em;

    // 취소된 일정도 포함(곡 상세 schedules)
    public List<PracticeSchedule> schedulesOf(Long songId) {
        return em.createQuery(
                        "select l.practiceSchedule from PracticeScheduleSong l where l.song.id = :songId"
                                + " order by l.practiceSchedule.startAt",
                        PracticeSchedule.class)
                .setParameter("songId", songId)
                .getResultList();
    }

    // S-10: 시작 전이고 취소되지 않은 일정과의 연결만 해제 대상. 지난 일정 연결은 유지
    public List<Long> upcomingLinks(Long songId) {
        return em.createQuery(
                        "select l.practiceSchedule.id from PracticeScheduleSong l where l.song.id = :songId"
                                + " and l.practiceSchedule.startAt > :now and l.practiceSchedule.status <> :canceled",
                        Long.class)
                .setParameter("songId", songId)
                .setParameter("now", LocalDateTime.now(ZoneOffset.UTC))
                .setParameter("canceled", ScheduleStatus.CANCELED)
                .getResultList();
    }

    @Transactional
    public List<Long> releaseUpcoming(Long songId) {
        List<Long> ids = upcomingLinks(songId);
        if (!ids.isEmpty()) {
            em.createQuery("delete from PracticeScheduleSong l where l.song.id = :songId"
                            + " and l.practiceSchedule.id in :ids")
                    .setParameter("songId", songId)
                    .setParameter("ids", ids)
                    .executeUpdate();
        }
        return ids;
    }
}
