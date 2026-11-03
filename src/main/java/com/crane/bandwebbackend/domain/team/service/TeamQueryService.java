package com.crane.bandwebbackend.domain.team.service;

import com.crane.bandwebbackend.domain.evaluation.service.EvaluationService;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationSnapshot;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongPartRequirement;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.repository.SongPartRequirementRepository;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.dto.TeamDetailResponse;
import com.crane.bandwebbackend.domain.team.dto.TeamResponse;
import com.crane.bandwebbackend.domain.team.dto.TeamResults;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.team.entity.TeamMember;
import com.crane.bandwebbackend.domain.team.entity.TeamStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 목록·상세(API-061·063). 후보곡 집계가 필요해 EvaluationService에 의존
 * (EvaluationService → TeamService 이므로 TeamService와 분리)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamQueryService {

    private final TeamService teamService;
    private final PerformanceAccessService accessService;
    private final SongSupport songSupport;
    private final SongPartRequirementRepository requirementRepository;
    private final EvaluationService evaluationService;

    // 정렬 고정: 기본 팀 먼저, 그다음 이름순
    public TeamResults.ListResult list(PerformanceRef ref, String status, boolean mine) {
        Set<TeamStatus> statuses = parseStatuses(status);
        PerformanceAccess access = accessService.get(ref);
        Long pid = access.performanceId();
        List<Team> teams = teamService.getTeams(pid);
        Map<Long, List<TeamMember>> members = teamService.membersOfPerformance(pid).stream()
                .collect(Collectors.groupingBy(m -> m.getTeam().getId()));
        List<Song> songs = songSupport.getAll(pid);
        Map<Long, Long> assigned = songs.stream()
                .filter(s -> s.getStatus() == SongStatus.CONFIRMED && s.getAssignedTeam() != null)
                .collect(Collectors.groupingBy(s -> s.getAssignedTeam().getId(), Collectors.counting()));
        Map<Long, Long> candidates = songs.stream()
                .filter(s -> s.getStatus() == SongStatus.PROPOSED && s.getCandidateTeam() != null)
                .collect(Collectors.groupingBy(s -> s.getCandidateTeam().getId(), Collectors.counting()));

        List<TeamResponse> content = teams.stream()
                .filter(t -> TeamService.canSee(access, t, memberIds(members, t)))
                .filter(t -> statuses.isEmpty() || statuses.contains(t.getStatus()))
                .filter(t -> !mine || memberIds(members, t).contains(access.myMemberId()))
                .sorted(Comparator.comparing((Team t) -> !t.isDefault()).thenComparing(Team::getName))
                .map(t -> teamService.toResponse(
                        access,
                        t,
                        members.getOrDefault(t.getId(), List.of()),
                        assigned.getOrDefault(t.getId(), 0L),
                        candidates.getOrDefault(t.getId(), 0L)))
                .toList();
        Map<TeamStatus, Integer> statusCounts = null;
        if (access.isAdmin()) {
            statusCounts = new EnumMap<>(TeamStatus.class);
            for (TeamStatus s : TeamStatus.values()) {
                statusCounts.put(s, (int) teams.stream().filter(t -> t.getStatus() == s).count());
            }
        }
        return TeamResults.ListResult.builder()
                .content(content)
                .statusCounts(statusCounts)
                .build();
    }

    public TeamDetailResponse detail(PerformanceRef ref, Long teamId) {
        PerformanceAccess access = accessService.get(ref);
        Long pid = access.performanceId();
        Team team = teamService.getTeam(pid, teamId);
        List<TeamMember> members = teamService.membersOf(teamId);
        Set<Long> memberIds =
                members.stream().map(m -> m.getPerformanceMember().getId()).collect(Collectors.toSet());
        if (!TeamService.canSee(access, team, memberIds)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다.");
        }
        boolean privileged = access.isAdmin() || memberIds.contains(access.myMemberId());
        List<Song> songs = songSupport.getAll(pid);
        List<Song> assigned = songs.stream()
                .filter(s -> s.getStatus() == SongStatus.CONFIRMED
                        && s.getAssignedTeam() != null
                        && s.getAssignedTeam().getId().equals(teamId))
                .sorted(Comparator.comparing(Song::getSetlistOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Song> candidates = songs.stream()
                .filter(s -> s.getStatus() == SongStatus.PROPOSED
                        && s.getCandidateTeam() != null
                        && s.getCandidateTeam().getId().equals(teamId))
                .toList();
        Map<Long, List<SongSessionMember>> sessions = songSupport.sessionsOfPerformance(pid).stream()
                .collect(Collectors.groupingBy(s -> s.getSong().getId()));
        Map<Long, List<SongPartRequirement>> requirements = requirementRepository.findByPerformanceId(pid).stream()
                .collect(Collectors.groupingBy(r -> r.getSong().getId()));
        EvaluationSnapshot snap = privileged ? evaluationService.snapshot(pid) : null;

        return TeamDetailResponse.builder()
                .team(teamService.toResponse(access, team, members, assigned.size(), candidates.size()))
                .members(teamService.toMemberResponses(access, team, members))
                .songs(assigned.stream()
                        .map(s -> TeamDetailResponse.AssignedSong.builder()
                                .id(s.getId())
                                .title(s.getTitle())
                                .artist(s.getArtist())
                                .setlistOrder(s.getSetlistOrder())
                                .sessionCount(sessions.getOrDefault(s.getId(), List.of()).size())
                                .requirementsSatisfied(SongSupport.requirementsSatisfied(
                                        requirements.getOrDefault(s.getId(), List.of()),
                                        sessions.getOrDefault(s.getId(), List.of())))
                                .version(s.getVersion())
                                .build())
                        .toList())
                .candidateSongs(
                        privileged
                                ? candidates.stream()
                                        .map(s -> TeamDetailResponse.CandidateSong.builder()
                                                .id(s.getId())
                                                .title(s.getTitle())
                                                .artist(s.getArtist())
                                                .evaluation(snap.summary(s))
                                                .version(s.getVersion())
                                                .build())
                                        .toList()
                                : List.of())
                .build();
    }

    private static Set<Long> memberIds(Map<Long, List<TeamMember>> members, Team team) {
        return members.getOrDefault(team.getId(), List.of()).stream()
                .map(m -> m.getPerformanceMember().getId())
                .collect(Collectors.toSet());
    }

    private static Set<TeamStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(status.split(","))
                    .map(String::strip)
                    .map(TeamStatus::valueOf)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_STATUS", "지원하지 않는 상태 값입니다.");
        }
    }
}
