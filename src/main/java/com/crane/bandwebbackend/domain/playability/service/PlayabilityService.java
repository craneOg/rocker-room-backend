package com.crane.bandwebbackend.domain.playability.service;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationRows;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationService;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationSnapshot;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.playability.dto.PlayabilityRequest;
import com.crane.bandwebbackend.domain.playability.entity.SongPlayability;
import com.crane.bandwebbackend.domain.playability.repository.SongPlayabilityRepository;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.audit.AuditLogRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가불 응답(API-090~094). 파트 단위 upsert, 미응답(행 없음)과 UNDECIDED를 구분.
 * 가불 파트는 본인 보유 파트가 기본이지만 다른 활성 파트도 허용
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayabilityService {

    private final SongPlayabilityRepository playabilityRepository;
    private final PerformanceAccessService accessService;
    private final SongSupport songSupport;
    private final TeamService teamService;
    private final EvaluationService evaluationService;
    private final AuditLogRepository auditLogRepository;

    /**
     * API-090. A는 대상 전체의 현재 원본과 비유효 원본(valid=false). 일반 사용자는 본인 행,
     * 응답 범위 안이고 playabilityResultPublic이면 범위 전체(V-09, UI-8). reason은 A만
     */
    public List<EvaluationRows.Playability> list(PerformanceRef ref, Long songId, Long performanceMemberId) {
        PerformanceAccess access = accessService.get(ref);
        Song song = songSupport.getSong(access.performanceId(), songId);
        EvaluationSnapshot snap = evaluationService.snapshot(access.performanceId());
        Long me = access.myMemberId();
        Set<Long> targets = snap.targets(song, false);
        boolean inScope = me != null && targets.contains(me);
        Set<Long> sessionIds =
                access.isGuest() ? EvaluationService.sessionMemberIds(songSupport.sessionsOf(songId)) : Set.of();
        if (!SongSupport.canSee(access, song, snap.teamMembers(), sessionIds) || (access.isGuest() && !inScope)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
        }
        List<SongPlayability> rows;
        if (access.isAdmin()) {
            rows = snap.allPlays(songId);
        } else if (inScope && accessService.getPolicy(access.performanceId()).isPlayabilityResultPublic()) {
            rows = snap.validPlays(song);
        } else {
            rows = snap.playsOf(songId, me);
        }
        return rows.stream()
                .filter(p -> performanceMemberId == null
                        || p.getPerformanceMember().getId().equals(performanceMemberId))
                .map(p -> EvaluationRows.Playability.of(
                        p,
                        access.isAdmin(),
                        access.isAdmin()
                                ? targets.contains(p.getPerformanceMember().getId())
                                : null))
                .toList();
    }

    // API-091 본인 저장
    @Transactional
    public EvaluationRows.Playability saveMine(PerformanceRef ref, Long songId, Long partId, PlayabilityRequest req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        PerformanceMember pm = access.requireParticipant();
        return save(access, songId, pm, partId, req);
    }

    // API-092 임원 대리 저장. 대상은 참여자(P), submittedBy = 요청한 임원
    @Transactional
    public EvaluationRows.Playability saveFor(
            PerformanceRef ref, Long songId, Long performanceMemberId, Long partId, PlayabilityRequest req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireAdmin();
        PerformanceMember pm = target(access, performanceMemberId);
        EvaluationRows.Playability row = save(access, songId, pm, partId, req);
        audit(access, "PLAYABILITY_PROXY_SAVED", songId, pm.getId(), partId);
        return row;
    }

    // API-093 본인 취소. 기존 응답이 없어도 204
    @Transactional
    public void deleteMine(PerformanceRef ref, Long songId, Long partId) {
        PerformanceAccess access = accessService.get(ref);
        PerformanceMember pm = access.requireParticipant();
        delete(access, songId, pm, partId);
    }

    // API-094 임원 대리 취소
    @Transactional
    public void deleteFor(PerformanceRef ref, Long songId, Long performanceMemberId, Long partId) {
        PerformanceAccess access = accessService.get(ref);
        access.requireAdmin();
        PerformanceMember pm = target(access, performanceMemberId);
        delete(access, songId, pm, partId);
        audit(access, "PLAYABILITY_PROXY_DELETED", songId, pm.getId(), partId);
    }

    private EvaluationRows.Playability save(
            PerformanceAccess access, Long songId, PerformanceMember pm, Long partId, PlayabilityRequest req) {
        Song song = requireInputOpen(access, songId, pm);
        Part part = accessService.getActivePart(access.clubId(), partId);
        // 조회 후 삽입(upsert). 호출부가 공연 행을 먼저 잠가(getForUpdate) 동시 저장이 uk_song_playability에 걸리지 않음
        var user = access.getMembership().getUser();
        SongPlayability row = playabilityRepository
                .findBySongIdAndPerformanceMemberIdAndPartId(songId, pm.getId(), part.getId())
                .map(p -> {
                    p.update(req.getAnswer(), req.getReason(), user);
                    return p;
                })
                .orElseGet(() -> playabilityRepository.save(
                        SongPlayability.of(song, pm, part, req.getAnswer(), req.getReason(), user)));
        playabilityRepository.flush();
        return EvaluationRows.Playability.of(row, access.isAdmin(), null);
    }

    private void delete(PerformanceAccess access, Long songId, PerformanceMember pm, Long partId) {
        requireInputOpen(access, songId, pm);
        playabilityRepository
                .findBySongIdAndPerformanceMemberIdAndPartId(songId, pm.getId(), partId)
                .ifPresent(playabilityRepository::delete);
    }

    // 공연 ACTIVE, 곡 PROPOSED, 가불 OPEN·마감 전, 현재 후보 범위(팀 범위면 그 팀원)
    private Song requireInputOpen(PerformanceAccess access, Long songId, PerformanceMember pm) {
        access.requireEditable();
        access.requireStatus(PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), songId);
        if (song.getStatus() != SongStatus.PROPOSED) {
            throw BusinessException.invalidState("후보곡에만 응답할 수 있습니다.");
        }
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        PerformanceAccess.requireOpen(
                policy.getPlayabilityState(), policy.getPlayabilityDeadline(), "PLAYABILITY_CLOSED");
        if (song.getCandidateTeam() != null
                && !teamService.isMember(song.getCandidateTeam().getId(), pm.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OUT_OF_CANDIDATE_SCOPE", "팀 후보곡은 팀원만 응답할 수 있습니다.");
        }
        return song;
    }

    private PerformanceMember target(PerformanceAccess access, Long performanceMemberId) {
        PerformanceMember pm = accessService.getMember(access.performanceId(), performanceMemberId);
        if (!pm.isActiveParticipant()) {
            throw BusinessException.invalidState("공연 참여자가 아닙니다.");
        }
        return pm;
    }

    private void audit(PerformanceAccess access, String action, Long songId, Long memberId, Long partId) {
        auditLogRepository.save(AuditLog.create(
                access.clubId(),
                access.userId(),
                action,
                "SONG",
                songId,
                null,
                "{\"performanceMemberId\":" + memberId + ",\"partId\":" + partId + "}"));
    }
}
