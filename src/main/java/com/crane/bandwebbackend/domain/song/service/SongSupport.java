package com.crane.bandwebbackend.domain.song.service;

import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongPartRequirement;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.entity.WithdrawalType;
import com.crane.bandwebbackend.domain.song.repository.SongRepository;
import com.crane.bandwebbackend.domain.song.repository.SongSessionMemberRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인(team·vote·playability·setlist·availability)이 쓰는 곡 조회와 연쇄 처리.
 * SongService는 TeamService에 의존하므로, TeamService가 곡을 다룰 때는 이 클래스를 씀(순환 의존 방지)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongSupport {

    private final SongRepository songRepository;
    private final SongSessionMemberRepository sessionMemberRepository;

    public Song getSong(Long performanceId, Long songId) {
        return songRepository
                .findByIdAndPerformanceId(songId, performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다."));
    }

    public List<Song> getAll(Long performanceId) {
        return songRepository.findAllWithRefs(performanceId);
    }

    public List<Song> getSongs(Long performanceId, List<SongStatus> statuses) {
        return songRepository.findByPerformanceIdAndStatusInOrderById(performanceId, statuses);
    }

    public List<SongSessionMember> sessionsOf(Long songId) {
        return sessionMemberRepository.findBySongId(songId);
    }

    public List<SongSessionMember> sessionsOfPerformance(Long performanceId) {
        return sessionMemberRepository.findByPerformanceId(performanceId);
    }

    public long countAssigned(Long teamId) {
        return songRepository.countByAssignedTeamIdAndStatus(teamId, SongStatus.CONFIRMED);
    }

    public long countCandidates(Long teamId) {
        return songRepository.countByCandidateTeamIdAndStatus(teamId, SongStatus.PROPOSED);
    }

    // 팀 범위 선정곡 수(API-072 확인 필요 규칙)
    public long countConfirmedInScope(Long teamId) {
        return songRepository.countByCandidateTeamIdAndStatus(teamId, SongStatus.CONFIRMED);
    }

    // UI-10·S-11: 팀 반려·철회 시 그 팀 범위 후보곡을 TEAM_WITHDRAWN으로. 철회 곡 ID 반환
    @Transactional
    public List<Long> withdrawCandidates(Long teamId) {
        List<Song> songs = songRepository.findByCandidateTeamIdAndStatus(teamId, SongStatus.PROPOSED);
        songs.forEach(s -> s.withdraw(WithdrawalType.TEAM_WITHDRAWN));
        return songs.stream().map(Song::getId).toList();
    }

    // API-067 removeSessions=true: 담당 팀 곡에서 그 팀원의 연주자 행 제거
    @Transactional
    public void removeSessions(Long performanceMemberId, Collection<Long> songIds) {
        if (!songIds.isEmpty()) {
            sessionMemberRepository.deleteByMemberAndSongs(performanceMemberId, songIds);
        }
    }

    /**
     * 곡 열람 범위(API-075·077·089).
     * A 전체 / GUEST는 관련 곡만(팀원·연주자·제안자) / 그 밖에는 팀 범위의 미선정 곡을 팀원만, 나머지 공개
     */
    public static boolean canSee(
            PerformanceAccess access, Song song, Map<Long, Set<Long>> teamMembers, Set<Long> sessionMemberIds) {
        if (access.isAdmin()) {
            return true;
        }
        Long me = access.myMemberId();
        boolean proposer =
                song.getProposedBy().getId().equals(access.getMembership().getId());
        boolean candidateMember = song.getCandidateTeam() != null
                && me != null
                && teamMembers
                        .getOrDefault(song.getCandidateTeam().getId(), Set.of())
                        .contains(me);
        if (access.isGuest()) {
            boolean assignedMember = song.getAssignedTeam() != null
                    && me != null
                    && teamMembers
                            .getOrDefault(song.getAssignedTeam().getId(), Set.of())
                            .contains(me);
            return proposer || candidateMember || assignedMember || (me != null && sessionMemberIds.contains(me));
        }
        if (song.getCandidateTeam() != null && song.getStatus() != SongStatus.CONFIRMED) {
            return proposer || candidateMember;
        }
        return true;
    }

    // 필요 편성 충족 여부(파트별 연주자 수 >= 필요 수). 필요 편성이 없으면 NULL
    public static Boolean requirementsSatisfied(
            List<SongPartRequirement> requirements, List<SongSessionMember> sessions) {
        if (requirements.isEmpty()) {
            return null;
        }
        Map<Long, Long> assigned =
                sessions.stream().collect(Collectors.groupingBy(m -> m.getPart().getId(), Collectors.counting()));
        return requirements.stream()
                .allMatch(r -> assigned.getOrDefault(r.getPart().getId(), 0L) >= r.getRequiredCount());
    }

    // 버전 증가를 응답에 반영
    public void flush() {
        songRepository.flush();
    }
}
