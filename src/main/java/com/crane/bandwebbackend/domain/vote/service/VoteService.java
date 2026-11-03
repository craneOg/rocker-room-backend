package com.crane.bandwebbackend.domain.vote.service;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationRows;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationService;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationSnapshot;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.domain.vote.dto.VoteRequest;
import com.crane.bandwebbackend.domain.vote.entity.SongVote;
import com.crane.bandwebbackend.domain.vote.repository.SongVoteRepository;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.audit.AuditLogRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일반 투표(API-095~099). 대상은 승인된 참여자(Q). 찬성 취소는 행 삭제. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {

    private final SongVoteRepository voteRepository;
    private final PerformanceAccessService accessService;
    private final SongSupport songSupport;
    private final TeamService teamService;
    private final EvaluationService evaluationService;
    private final AuditLogRepository auditLogRepository;

    // API-095. 권한·공개 규칙은 API-090과 같음(voteResultPublic)
    public List<EvaluationRows.Vote> list(PerformanceRef ref, Long songId, Long performanceMemberId) {
        PerformanceAccess access = accessService.get(ref);
        Song song = songSupport.getSong(access.performanceId(), songId);
        EvaluationSnapshot snap = evaluationService.snapshot(access.performanceId());
        Long me = access.myMemberId();
        boolean inScope = me != null && snap.targets(song, false).contains(me);
        Set<Long> sessionIds =
                access.isGuest() ? EvaluationService.sessionMemberIds(songSupport.sessionsOf(songId)) : Set.of();
        // GUEST는 무관한 곡을 404
        if (!SongSupport.canSee(access, song, snap.teamMembers(), sessionIds) || (access.isGuest() && !inScope)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
        }
        Set<Long> targets = snap.targets(song, true);
        List<SongVote> rows;
        if (access.isAdmin()) {
            rows = snap.allVotes(songId);
        } else if (inScope && accessService.getPolicy(access.performanceId()).isVoteResultPublic()) {
            rows = snap.validVotes(song);
        } else {
            rows = snap.voteOf(songId, me).stream().toList();
        }
        return rows.stream()
                .filter(v -> performanceMemberId == null
                        || v.getPerformanceMember().getId().equals(performanceMemberId))
                .map(v -> EvaluationRows.Vote.of(
                        v,
                        access.isAdmin()
                                ? targets.contains(v.getPerformanceMember().getId())
                                : null))
                .toList();
    }

    // API-096
    @Transactional
    public EvaluationRows.Vote voteMine(PerformanceRef ref, Long songId, VoteRequest req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        return save(access, songId, access.requireApprovedParticipant(), req.getScore());
    }

    // API-097 임원 대리. submittedBy = 요청한 임원, 한도 검사는 본인 입력과 같음
    @Transactional
    public EvaluationRows.Vote voteFor(PerformanceRef ref, Long songId, Long performanceMemberId, VoteRequest req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireAdmin();
        PerformanceMember pm = target(access, performanceMemberId);
        EvaluationRows.Vote row = save(access, songId, pm, req.getScore());
        audit(access, "VOTE_PROXY_SAVED", songId, pm.getId());
        return row;
    }

    // API-098. 기존 행이 없어도 204
    @Transactional
    public void deleteMine(PerformanceRef ref, Long songId) {
        PerformanceAccess access = accessService.get(ref);
        delete(access, songId, access.requireApprovedParticipant());
    }

    // API-099
    @Transactional
    public void deleteFor(PerformanceRef ref, Long songId, Long performanceMemberId) {
        PerformanceAccess access = accessService.get(ref);
        access.requireAdmin();
        PerformanceMember pm = target(access, performanceMemberId);
        delete(access, songId, pm);
        audit(access, "VOTE_PROXY_DELETED", songId, pm.getId());
    }

    private EvaluationRows.Vote save(PerformanceAccess access, Long songId, PerformanceMember pm, int score) {
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        Song song = requireInputOpen(access, songId, pm, policy);
        validateScore(policy.getVoteMethod(), score);
        // V-05: 호출부가 공연 행을 먼저 잠가(getForUpdate) 스냅샷이 앞 트랜잭션 커밋 뒤에 생김. 기존 행 변경은 초과 건수에 넣지 않음
        var user = access.getMembership().getUser();
        SongVote vote = voteRepository
                .findBySongIdAndPerformanceMemberId(songId, pm.getId())
                .orElse(null);
        if (vote == null) {
            Integer max = policy.getVoteMaxChoices();
            if (policy.getVoteMethod() == VoteMethod.APPROVAL
                    && max != null
                    && voteRepository.countByMemberAndSongStatus(pm.getId(), SongStatus.PROPOSED) >= max) {
                throw new BusinessException(
                        ErrorCode.RULE_VIOLATION, "MAX_APPROVALS_EXCEEDED", "최대 " + max + "곡까지 찬성할 수 있습니다.");
            }
            vote = voteRepository.save(SongVote.of(song, pm, score, user));
        } else {
            vote.update(score, user);
        }
        voteRepository.flush();
        return EvaluationRows.Vote.of(vote, null);
    }

    private void delete(PerformanceAccess access, Long songId, PerformanceMember pm) {
        requireInputOpen(access, songId, pm, accessService.getPolicy(access.performanceId()));
        voteRepository.findBySongIdAndPerformanceMemberId(songId, pm.getId()).ifPresent(voteRepository::delete);
    }

    // V-04
    static void validateScore(VoteMethod method, int score) {
        boolean ok = method == VoteMethod.APPROVAL ? score == 1 : score >= 1 && score <= 5;
        if (!ok) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_SCORE", "투표 방식에 맞지 않는 점수입니다.");
        }
    }

    // 공연 ACTIVE, 곡 PROPOSED, 투표 OPEN·마감 전, 후보 범위 팀 검사
    private Song requireInputOpen(
            PerformanceAccess access, Long songId, PerformanceMember pm, PerformancePolicy policy) {
        access.requireEditable();
        access.requireStatus(PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), songId);
        if (song.getStatus() != SongStatus.PROPOSED) {
            throw BusinessException.invalidState("후보곡에만 투표할 수 있습니다.");
        }
        PerformanceAccess.requireOpen(policy.getVoteState(), policy.getVoteDeadline(), "VOTE_CLOSED");
        if (song.getCandidateTeam() != null
                && !teamService.isMember(song.getCandidateTeam().getId(), pm.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OUT_OF_CANDIDATE_SCOPE", "팀 후보곡은 팀원만 투표할 수 있습니다.");
        }
        return song;
    }

    private PerformanceMember target(PerformanceAccess access, Long performanceMemberId) {
        PerformanceMember pm = accessService.getMember(access.performanceId(), performanceMemberId);
        if (pm.getStatus() != PerformanceMemberStatus.APPROVED) {
            throw BusinessException.invalidState("승인된 참여자만 투표 대상입니다.");
        }
        return pm;
    }

    private void audit(PerformanceAccess access, String action, Long songId, Long memberId) {
        auditLogRepository.save(AuditLog.of(
                access.clubId(),
                access.userId(),
                action,
                "SONG",
                songId,
                null,
                "{\"performanceMemberId\":" + memberId + "}"));
    }
}
