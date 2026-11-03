package com.crane.bandwebbackend.domain.evaluation.service;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationMatrixResponse;
import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationRows;
import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationType;
import com.crane.bandwebbackend.domain.evaluation.dto.SongEvaluationResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.playability.entity.SongPlayability;
import com.crane.bandwebbackend.domain.playability.repository.SongPlayabilityRepository;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.domain.vote.entity.SongVote;
import com.crane.bandwebbackend.domain.vote.repository.SongVoteRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    private static final List<PerformanceMemberStatus> ACTIVE =
            List.of(PerformanceMemberStatus.APPLIED, PerformanceMemberStatus.APPROVED);

    private final SongPlayabilityRepository playabilityRepository;
    private final SongVoteRepository voteRepository;
    private final PerformanceAccessService accessService;
    private final TeamService teamService;
    private final SongSupport songSupport;

    public EvaluationSnapshot snapshot(Long performanceId) {
        return new EvaluationSnapshot(
                accessService.getPolicy(performanceId).getVoteMethod(),
                accessService.getMembers(performanceId, ACTIVE),
                teamService.memberIdsByTeam(performanceId),
                playabilityRepository.findByPerformanceId(performanceId),
                voteRepository.findByPerformanceId(performanceId));
    }

    // API-081 resetEvaluations
    @Transactional
    public void deleteForSong(Long songId) {
        voteRepository.deleteBySongId(songId);
        playabilityRepository.deleteBySongId(songId);
    }

    /** API-089 곡 하나의 집계 + 공개 범위에 따른 응답자 목록(V-09, UI-8). */
    public SongEvaluationResponse songEvaluation(PerformanceRef ref, Long songId) {
        PerformanceAccess access = accessService.get(ref);
        Song song = songSupport.getSong(access.performanceId(), songId);
        EvaluationSnapshot snap = snapshot(access.performanceId());
        Set<Long> sessionIds = access.isGuest()
                ? songSupport.sessionsOf(songId).stream()
                        .map(s -> s.getPerformanceMember().getId())
                        .collect(Collectors.toSet())
                : Set.of();
        Long me = access.myMemberId();
        boolean inScope = me != null && snap.targets(song, false).contains(me);
        // GUEST는 본인이 평가할 수 있는 곡만
        if (!SongSupport.canSee(access, song, snap.teamMembers(), sessionIds) || (access.isGuest() && !inScope)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
        }
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        boolean playNames = access.isAdmin() || (inScope && policy.isPlayabilityResultPublic());
        boolean voteNames = access.isAdmin() || (inScope && policy.isVoteResultPublic());

        List<SongPlayability> plays = snap.validPlays(song);
        List<SongVote> votes = snap.validVotes(song);
        Set<Long> answeredPlay =
                plays.stream().map(p -> p.getPerformanceMember().getId()).collect(Collectors.toSet());
        Set<Long> answeredVote =
                votes.stream().map(v -> v.getPerformanceMember().getId()).collect(Collectors.toSet());
        return SongEvaluationResponse.builder()
                .songId(song.getId())
                .voteMethod(snap.voteMethod())
                .summary(snap.summary(song))
                .namesVisible(SongEvaluationResponse.Pair.builder()
                        .playability(playNames)
                        .vote(voteNames)
                        .build())
                .playabilityResponses(
                        playNames
                                ? plays.stream()
                                        .map(p -> EvaluationRows.Playability.of(p, access.isAdmin(), null))
                                        .toList()
                                : null)
                .voteResponses(
                        voteNames
                                ? votes.stream()
                                        .map(v -> EvaluationRows.Vote.of(v, null))
                                        .toList()
                                : null)
                .nonResponders(SongEvaluationResponse.NonResponders.builder()
                        .playability(playNames ? nonResponders(snap, snap.targets(song, false), answeredPlay) : null)
                        .vote(voteNames ? nonResponders(snap, snap.targets(song, true), answeredVote) : null)
                        .build())
                .build();
    }

    /**
     * API-158 SONG-005 곡×사람 표. 대상 곡은 후보(PROPOSED), 열(members)은 teamId가 있으면 그 팀원, 없으면 공연 참여자.
     * 응답률은 열린(OPEN) 기능만 포함.
     */
    public EvaluationMatrixResponse matrix(PerformanceRef ref, EvaluationType type, Long teamId) {
        PerformanceAccess access = accessService.get(ref);
        EvaluationType tab = type == null ? EvaluationType.PLAYABILITY : type;
        EvaluationSnapshot snap = snapshot(access.performanceId());
        // 볼 수 없는 팀은 존재를 숨김(UI-9, G-04)
        if (teamId != null
                && !TeamService.canSee(
                        access, teamService.getTeam(access.performanceId(), teamId), snap.teamMemberIds(teamId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다.");
        }
        Long me = access.myMemberId();
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());

        List<Song> songs = songSupport.getSongs(access.performanceId(), List.of(SongStatus.PROPOSED)).stream()
                .filter(s -> SongSupport.canSee(access, s, snap.teamMembers(), Set.of()))
                // GUEST는 본인이 응답할 수 있는 곡만(API-089와 같은 기준)
                .filter(s -> !access.isGuest()
                        || (me != null && snap.targets(s, false).contains(me)))
                .filter(s -> teamId == null
                        || s.getCandidateTeam() == null
                        || s.getCandidateTeam().getId().equals(teamId))
                .toList();
        Set<Long> columnIds = teamId == null ? snap.members().keySet() : snap.teamMemberIds(teamId);
        List<PerformanceMember> columns = snap.members().values().stream()
                .filter(pm -> columnIds.contains(pm.getId()))
                .filter(pm -> tab == EvaluationType.PLAYABILITY || pm.getStatus() == PerformanceMemberStatus.APPROVED)
                .toList();
        Set<Long> columnSet = columns.stream().map(PerformanceMember::getId).collect(Collectors.toSet());

        boolean participant = me != null && snap.members().containsKey(me);
        boolean playNames = access.isAdmin() || (participant && policy.isPlayabilityResultPublic());
        boolean voteNames = access.isAdmin() || (participant && policy.isVoteResultPublic());
        boolean tabNames = tab == EvaluationType.PLAYABILITY ? playNames : voteNames;

        List<EvaluationRows.Playability> plays = new ArrayList<>();
        List<EvaluationRows.Vote> votes = new ArrayList<>();
        List<EvaluationRows.Member> nonResponders = new ArrayList<>();
        Set<Long> answered = new HashSet<>();
        boolean playOpen = policy.getPlayabilityState() == FeatureState.OPEN;
        boolean voteOpen = policy.getVoteState() == FeatureState.OPEN;
        Map<Long, Integer> unresponded = new HashMap<>();

        for (Song song : songs) {
            Set<Long> playTargets = snap.targets(song, false);
            Set<Long> voteTargets = snap.targets(song, true);
            List<SongPlayability> songPlays = snap.validPlays(song).stream()
                    .filter(p -> columnSet.contains(p.getPerformanceMember().getId()))
                    .toList();
            List<SongVote> songVotes = snap.validVotes(song).stream()
                    .filter(v -> columnSet.contains(v.getPerformanceMember().getId()))
                    .toList();
            songPlays.stream()
                    .filter(p -> playNames || p.getPerformanceMember().getId().equals(me))
                    .forEach(p -> plays.add(EvaluationRows.Playability.of(p, access.isAdmin(), null)));
            songVotes.stream()
                    .filter(v -> voteNames || v.getPerformanceMember().getId().equals(me))
                    .forEach(v -> votes.add(EvaluationRows.Vote.of(v, null)));
            if (playOpen) {
                songPlays.forEach(p -> answered.add(p.getPerformanceMember().getId()));
            }
            if (voteOpen) {
                songVotes.forEach(v -> answered.add(v.getPerformanceMember().getId()));
            }
            Set<Long> tabTargets = tab == EvaluationType.PLAYABILITY ? playTargets : voteTargets;
            Set<Long> tabAnswered = (tab == EvaluationType.PLAYABILITY
                            ? songPlays.stream()
                                    .map(p -> p.getPerformanceMember().getId())
                            : songVotes.stream()
                                    .map(v -> v.getPerformanceMember().getId()))
                    .collect(Collectors.toSet());
            columns.stream()
                    .map(PerformanceMember::getId)
                    .filter(id -> tabTargets.contains(id) && !tabAnswered.contains(id))
                    .forEach(id -> unresponded.merge(id, 1, Integer::sum));
        }
        for (PerformanceMember pm : columns) {
            Integer count = unresponded.get(pm.getId());
            if (count != null) {
                nonResponders.add(EvaluationRows.Member.builder()
                        .performanceMemberId(pm.getId())
                        .displayName(PerformanceAccessService.displayName(pm))
                        .unrespondedSongCount(count)
                        .build());
            }
        }
        return EvaluationMatrixResponse.builder()
                .voteMethod(snap.voteMethod())
                .type(tab)
                .songs(songs.stream()
                        .map(s -> EvaluationMatrixResponse.SongColumn.builder()
                                .id(s.getId())
                                .title(s.getTitle())
                                .artist(s.getArtist())
                                .candidateTeamId(
                                        s.getCandidateTeam() == null
                                                ? null
                                                : s.getCandidateTeam().getId())
                                .build())
                        .toList())
                .members(
                        tabNames
                                ? columns.stream()
                                        .map(EvaluationRows.Member::of)
                                        .toList()
                                : null)
                .playability(plays)
                .votes(votes)
                .responseRate(
                        playOpen || voteOpen
                                ? EvaluationMatrixResponse.ResponseRate.builder()
                                        .total(columns.size())
                                        .answered((int) columns.stream()
                                                .filter(pm -> answered.contains(pm.getId()))
                                                .count())
                                        .build()
                                : null)
                .nonResponders(tabNames ? nonResponders : null)
                .build();
    }

    // 곡 상세 GUEST 판단 등에서 연주자 ID 집합
    public static Set<Long> sessionMemberIds(List<SongSessionMember> sessions) {
        return sessions.stream().map(s -> s.getPerformanceMember().getId()).collect(Collectors.toSet());
    }

    private static List<EvaluationRows.Member> nonResponders(
            EvaluationSnapshot snap, Set<Long> targets, Set<Long> answered) {
        return targets.stream()
                .filter(id -> !answered.contains(id))
                .map(id -> EvaluationRows.Member.of(snap.members().get(id)))
                .toList();
    }
}
