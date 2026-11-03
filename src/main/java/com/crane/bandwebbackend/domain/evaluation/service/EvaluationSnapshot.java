package com.crane.bandwebbackend.domain.evaluation.service;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationSummary;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.domain.playability.entity.PlayabilityAnswer;
import com.crane.bandwebbackend.domain.playability.entity.SongPlayability;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.vote.entity.SongVote;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 공연 하나의 가불·투표 원본을 한 번에 읽어 둔 것. 집계 규칙(P-07)
 * - 응답 범위: 팀 범위 후보면 그 팀의 팀원, 아니면 공연 참여자
 * - 가불은 APPLIED·APPROVED 응답 포함, 투표는 APPROVED만(APPLIED 투표 제외)
 * - 철회·반려된 참여자와 범위 밖 응답은 집계·목록에서 뺌(원본은 보존)
 */
public class EvaluationSnapshot {

    private final VoteMethod voteMethod;
    // APPLIED·APPROVED 참여자
    private final Map<Long, PerformanceMember> members;
    private final Map<Long, Set<Long>> teamMembers;
    private final Map<Long, List<SongPlayability>> playsBySong;
    private final Map<Long, List<SongVote>> votesBySong;

    EvaluationSnapshot(
            VoteMethod voteMethod,
            List<PerformanceMember> members,
            Map<Long, Set<Long>> teamMembers,
            List<SongPlayability> plays,
            List<SongVote> votes) {
        this.voteMethod = voteMethod;
        this.members = members.stream()
                .collect(Collectors.toMap(PerformanceMember::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        this.teamMembers = teamMembers;
        this.playsBySong = plays.stream().collect(Collectors.groupingBy(p -> p.getSong().getId()));
        this.votesBySong = votes.stream().collect(Collectors.groupingBy(v -> v.getSong().getId()));
    }

    public VoteMethod voteMethod() {
        return voteMethod;
    }

    public Map<Long, PerformanceMember> members() {
        return members;
    }

    public Set<Long> teamMemberIds(Long teamId) {
        return teamMembers.getOrDefault(teamId, Set.of());
    }

    public Map<Long, Set<Long>> teamMembers() {
        return teamMembers;
    }

    // 응답 대상자. vote = true면 APPROVED만
    public Set<Long> targets(Song song, boolean vote) {
        return members.values().stream()
                .filter(pm -> !vote || pm.getStatus() == PerformanceMemberStatus.APPROVED)
                .map(PerformanceMember::getId)
                .filter(id -> song.getCandidateTeam() == null
                        || teamMemberIds(song.getCandidateTeam().getId()).contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<SongPlayability> allPlays(Long songId) {
        return playsBySong.getOrDefault(songId, List.of());
    }

    public List<SongVote> allVotes(Long songId) {
        return votesBySong.getOrDefault(songId, List.of());
    }

    public List<SongPlayability> validPlays(Song song) {
        Set<Long> targets = targets(song, false);
        return allPlays(song.getId()).stream()
                .filter(p -> targets.contains(p.getPerformanceMember().getId()))
                .toList();
    }

    public List<SongVote> validVotes(Song song) {
        Set<Long> targets = targets(song, true);
        return allVotes(song.getId()).stream()
                .filter(v -> targets.contains(v.getPerformanceMember().getId()))
                .toList();
    }

    public List<SongPlayability> playsOf(Long songId, Long memberId) {
        return allPlays(songId).stream()
                .filter(p -> p.getPerformanceMember().getId().equals(memberId))
                .toList();
    }

    public Optional<SongVote> voteOf(Long songId, Long memberId) {
        return allVotes(songId).stream()
                .filter(v -> v.getPerformanceMember().getId().equals(memberId))
                .findFirst();
    }

    // 정렬 possibleCount,desc: 가능(POSSIBLE)으로 응답한 서로 다른 참여자 수
    public int possibleCount(Song song) {
        return (int) validPlays(song).stream()
                .filter(p -> p.getAnswer() == PlayabilityAnswer.POSSIBLE)
                .map(p -> p.getPerformanceMember().getId())
                .distinct()
                .count();
    }

    public EvaluationSummary summary(Song song) {
        List<SongPlayability> plays = validPlays(song);
        Map<PlayabilityAnswer, Integer> byAnswer = new EnumMap<>(PlayabilityAnswer.class);
        for (PlayabilityAnswer a : PlayabilityAnswer.values()) {
            byAnswer.put(
                    a,
                    (int) plays.stream()
                            .filter(p -> p.getAnswer() == a)
                            .map(p -> p.getPerformanceMember().getId())
                            .distinct()
                            .count());
        }
        Map<Long, List<SongPlayability>> byPart = plays.stream()
                .collect(Collectors.groupingBy(p -> p.getPart().getId(), TreeMap::new, Collectors.toList()));
        List<EvaluationSummary.PartPlayability> parts = byPart.values().stream()
                .map(rows -> EvaluationSummary.PartPlayability.builder()
                        .partId(rows.get(0).getPart().getId())
                        .partName(rows.get(0).getPart().getName())
                        .possible(count(rows, PlayabilityAnswer.POSSIBLE))
                        .impossible(count(rows, PlayabilityAnswer.IMPOSSIBLE))
                        .undecided(count(rows, PlayabilityAnswer.UNDECIDED))
                        .build())
                .toList();

        List<SongVote> votes = validVotes(song);
        Double average = null;
        Map<String, Integer> distribution = null;
        if (voteMethod == VoteMethod.SCORE) {
            average = votes.isEmpty()
                    ? null
                    : votes.stream().mapToInt(SongVote::getScore).average().orElse(0);
            distribution = new LinkedHashMap<>();
            for (int score = 1; score <= 5; score++) {
                int s = score;
                distribution.put(String.valueOf(score), (int)
                        votes.stream().filter(v -> v.getScore() == s).count());
            }
        }
        return EvaluationSummary.builder()
                .playability(byAnswer)
                .playabilityByPart(parts)
                .voteCount(votes.size())
                .scoreAverage(average)
                .scoreDistribution(distribution)
                .build();
    }

    private static int count(List<SongPlayability> rows, PlayabilityAnswer answer) {
        return (int) rows.stream().filter(p -> p.getAnswer() == answer).count();
    }
}
