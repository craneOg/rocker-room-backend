package com.crane.bandwebbackend.domain.performance.service;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 잠금 준비 상태를 조회하며 정규화 순서 쓰기는 LockService에 맡긴다. */
@Component
@RequiredArgsConstructor
public class LockReadinessReader {
    private final EntityManager entityManager;

    /** 공연 확정 점검에 필요한 곡·팀·참여자·오디션 정보를 조회한다. */
    @Transactional(readOnly = true)
    public LockChecklistEvaluator.Snapshot read(Long performanceId) {
        // 확정 곡과 담당 팀 상태를 잠금 평가용 행으로 변환한다.
        List<LockChecklistEvaluator.SongRow> songs = rows("""
				SELECT s.id, s.setlist_order, s.assigned_team_id, t.status
				FROM song s LEFT JOIN team t ON t.id = s.assigned_team_id
				WHERE s.performance_id = :performanceId AND s.status = 'CONFIRMED'
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.SongRow(
                        number(row[0]),
                        row[1] == null ? null : ((Number) row[1]).intValue(),
                        row[2] == null ? null : number(row[2]),
                        (String) row[3]))
                .toList();
        // 곡별 연주자·파트와 현재 참여 상태를 함께 조회한다.
        List<LockChecklistEvaluator.PlayerRow> players = rows("""
				SELECT ssm.song_id, ssm.performance_member_id, ssm.part_id, pm.status
				FROM song_session_member ssm
				JOIN song s ON s.id = ssm.song_id
				JOIN performance_member pm ON pm.id = ssm.performance_member_id
				WHERE s.performance_id = :performanceId AND s.status = 'CONFIRMED'
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.PlayerRow(
                        number(row[0]), number(row[1]), number(row[2]), (String) row[3]))
                .toList();
        List<LockChecklistEvaluator.TeamRow> teams = rows("""
				SELECT id, status FROM team WHERE performance_id = :performanceId AND status = 'SUBMITTED'
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.TeamRow(number(row[0]), (String) row[1]))
                .toList();
        List<LockChecklistEvaluator.ParticipantRow> participants = rows("""
				SELECT id, status FROM performance_member
				WHERE performance_id = :performanceId AND status IN ('APPLIED', 'APPROVED')
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.ParticipantRow(number(row[0]), (String) row[1]))
                .toList();
        List<LockChecklistEvaluator.RequirementRow> requirements = rows("""
				SELECT r.song_id, r.part_id, p.name, r.required_count
				FROM song_part_requirement r
				JOIN song s ON s.id = r.song_id
				JOIN part p ON p.id = r.part_id
				WHERE s.performance_id = :performanceId AND s.status = 'CONFIRMED'
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.RequirementRow(
                        number(row[0]), number(row[1]), (String) row[2], ((Number) row[3]).intValue()))
                .toList();
        List<LockChecklistEvaluator.AuditionRow> auditions = rows("""
				SELECT id, song_id, performance_member_id, result, created_at
				FROM audition_entry WHERE performance_id = :performanceId
				""", performanceId).stream()
                .map(row -> new LockChecklistEvaluator.AuditionRow(
                        number(row[0]),
                        number(row[1]),
                        row[2] == null ? null : number(row[2]),
                        (String) row[3],
                        dateTime(row[4])))
                .toList();
        // 정책의 권장 곡 수를 더해 모든 조회 결과를 하나의 불변 스냅샷으로 묶는다.
        List<?> limits = entityManager
                .createNativeQuery("""
				SELECT max_songs_per_member FROM performance_policy WHERE performance_id = :performanceId
				""")
                .setParameter("performanceId", performanceId)
                .getResultList();
        Number max = limits.isEmpty() ? null : (Number) limits.get(0);
        return new LockChecklistEvaluator.Snapshot(
                songs, players, teams, participants, requirements, auditions, max == null ? null : max.intValue());
    }

    @SuppressWarnings("unchecked")
    /** 공연 식별자를 바인딩해 잠금 점검용 행 목록을 조회한다. */
    private List<Object[]> rows(String sql, Long performanceId) {
        return entityManager
                .createNativeQuery(sql)
                .setParameter("performanceId", performanceId)
                .getResultList();
    }

    /** 네이티브 쿼리 숫자 값을 long으로 변환한다. */
    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    /** JDBC가 반환한 날짜 값을 UTC 로컬 시각으로 변환한다. */
    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp stamp) return stamp.toLocalDateTime();
        throw new IllegalStateException("오디션 생성 시각 형식을 읽을 수 없습니다.");
    }
}
