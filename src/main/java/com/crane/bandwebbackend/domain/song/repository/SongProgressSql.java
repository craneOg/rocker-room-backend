package com.crane.bandwebbackend.domain.song.repository;

// 가불·투표 진행도 SQL 조각. 홈 할 일(API-137)과 내 참여 현황(API-054)이 같은 계산을 쓰도록 공유한다.
// 별칭 s(song)·p(performance)·pm(performance_member)와 이름 파라미터 :guest, :mid(멤버십 id)가 필요하다
public final class SongProgressSql {

    private SongProgressSql() {}

    // 내(pm) 기준 가불·투표 대상 곡 조건(별칭 s, p, pm 사용)
    public static final String ANSWERABLE = "s.performance_id = p.id AND s.status = 'PROPOSED'"
            + " AND (s.candidate_team_id IS NULL OR EXISTS (SELECT 1 FROM team_member tm"
            + " WHERE tm.team_id = s.candidate_team_id AND tm.performance_member_id = pm.id))"
            + " AND (:guest = FALSE OR s.proposed_by_membership_id = :mid OR s.candidate_team_id IS NOT NULL)";

    // 대상 곡 중 내 가불 응답 행이 하나도 없는 곡 수
    public static final String UNANSWERED = "(SELECT COUNT(*) FROM song s WHERE " + ANSWERABLE
            + " AND NOT EXISTS (SELECT 1 FROM song_playability sp"
            + " WHERE sp.song_id = s.id AND sp.performance_member_id = pm.id))";

    // V-05: PROPOSED 곡에 한 내 투표 수
    public static final String MY_VOTES = "(SELECT COUNT(*) FROM song_vote v JOIN song s ON s.id = v.song_id"
            + " WHERE v.performance_member_id = pm.id AND s.status = 'PROPOSED')";
}
