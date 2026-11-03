package com.crane.bandwebbackend.domain.song.service;

import com.crane.bandwebbackend.domain.audition.service.AuditionQueryService;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationService;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationSnapshot;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.CandidateScope;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleLinkService;
import com.crane.bandwebbackend.domain.song.dto.SongRequests;
import com.crane.bandwebbackend.domain.song.dto.SongResponse;
import com.crane.bandwebbackend.domain.song.dto.SongViews;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongPartRequirement;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.entity.WithdrawalType;
import com.crane.bandwebbackend.domain.song.repository.SongPartRequirementRepository;
import com.crane.bandwebbackend.domain.song.repository.SongRepository;
import com.crane.bandwebbackend.domain.song.repository.SongSessionMemberRepository;
import com.crane.bandwebbackend.domain.team.dto.TeamRequests;
import com.crane.bandwebbackend.domain.team.dto.TeamResults;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.team.entity.TeamStatus;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.audit.AuditLogRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.Effects;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.response.Warning;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곡(API-075~086, 156, 157). A = 임원, P = 공연 참여자, L = 담당 팀 팀장.
 * LOCKED 공연의 변경은 예외 수정 경로(X-01): 사유 필수, 공연 행 잠금(X-03), 저장 후 L-01~L-03 재검사(X-04), 감사 기록(X-07)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<MembershipStatus> VALID_MEMBERSHIP =
            Set.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE, MembershipStatus.GRADUATED);

    private final SongRepository songRepository;
    private final SongSessionMemberRepository sessionMemberRepository;
    private final SongPartRequirementRepository requirementRepository;
    private final SongSupport songSupport;
    private final PerformanceAccessService accessService;
    private final TeamService teamService;
    private final EvaluationService evaluationService;
    private final ScheduleLinkService scheduleLinkService;
    private final AuditionQueryService auditionQueryService;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager em;

    // ---------- API-075 곡 목록 ----------

    public SongViews.ListResult list(
            PerformanceRef ref,
            String status,
            Long candidateTeamId,
            boolean proposedByMe,
            boolean unresponded,
            String sort,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_PAGE", "page·size가 올바르지 않습니다.");
        }
        SongSort order = SongSort.parse(sort);
        Set<SongStatus> statuses = parseStatuses(status);
        PerformanceAccess access = accessService.get(ref);
        Long pid = access.performanceId();
        EvaluationSnapshot snap = evaluationService.snapshot(pid);
        PerformancePolicy policy = accessService.getPolicy(pid);
        Map<Long, List<SongSessionMember>> sessions = sessionsBySong(pid);
        Map<Long, List<SongPartRequirement>> requirements = requirementRepository.findByPerformanceId(pid).stream()
                .collect(Collectors.groupingBy(r -> r.getSong().getId()));
        Long me = access.myMemberId();
        Long myMembership = access.getMembership().getId();

        List<Song> all = songSupport.getAll(pid);
        List<Song> visible = all.stream()
                .filter(s -> SongSupport.canSee(access, s, snap.teamMembers(), memberIds(sessions, s)))
                .toList();
        List<Song> filtered = visible.stream()
                .filter(s -> statuses.isEmpty() || statuses.contains(s.getStatus()))
                .filter(s -> candidateTeamId == null
                        || (s.getCandidateTeam() != null && s.getCandidateTeam().getId().equals(candidateTeamId)))
                .filter(s -> !proposedByMe || s.getProposedBy().getId().equals(myMembership))
                .filter(s -> !unresponded || isUnresponded(s, snap, policy, me))
                .sorted(comparator(order, snap))
                .toList();

        List<SongViews.Card> cards = filtered.stream()
                .map(s -> card(s, snap, sessions, requirements, me))
                .toList();
        int myProposalCount = (int) all.stream()
                .filter(s -> s.getProposedBy().getId().equals(myMembership) && s.getStatus() != SongStatus.WITHDRAWN)
                .count();
        Integer myVoteCount = snap.voteMethod() == VoteMethod.APPROVAL
                ? (int) all.stream()
                        .filter(s -> s.getStatus() == SongStatus.PROPOSED)
                        .filter(s -> me != null && snap.voteOf(s.getId(), me).isPresent())
                        .count()
                : null;
        return SongViews.ListResult.builder()
                .page(new PageResponse<>(cards, page, size))
                .myProposalCount(myProposalCount)
                .myVoteCount(myVoteCount)
                .build();
    }

    private static Set<SongStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(status.split(","))
                    .map(String::strip)
                    .map(SongStatus::valueOf)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_STATUS", "지원하지 않는 상태 값입니다.");
        }
    }

    // 내가 응답할 수 있는 후보곡 중 사용 중인 기능(가불·투표)에 아직 응답하지 않은 곡
    private static boolean isUnresponded(Song s, EvaluationSnapshot snap, PerformancePolicy policy, Long me) {
        if (me == null || s.getStatus() != SongStatus.PROPOSED) {
            return false;
        }
        boolean playPending = policy.getPlayabilityState() == FeatureState.OPEN
                && snap.targets(s, false).contains(me)
                && snap.playsOf(s.getId(), me).isEmpty();
        boolean votePending = policy.getVoteState() == FeatureState.OPEN
                && snap.targets(s, true).contains(me)
                && snap.voteOf(s.getId(), me).isEmpty();
        return playPending || votePending;
    }

    private static Comparator<Song> comparator(SongSort sort, EvaluationSnapshot snap) {
        Comparator<Song> recent =
                Comparator.comparing(Song::getCreatedAt, Comparator.reverseOrder()).thenComparing(Song::getId, Comparator.reverseOrder());
        return switch (sort) {
            case CREATED_AT_DESC -> recent;
            case SETLIST_ORDER_ASC -> Comparator.comparing(
                            Song::getSetlistOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Song::getId);
            case TITLE_ASC -> Comparator.comparing(Song::getTitle).thenComparing(Song::getId);
            case POSSIBLE_COUNT_DESC -> Comparator.comparing((Song s) -> snap.possibleCount(s), Comparator.reverseOrder())
                    .thenComparing(recent);
            // 점수 방식이면 평균 점수 순, 동률은 투표 수 → 최근 제안 순
            case VOTE_COUNT_DESC -> {
                Comparator<Song> byCount = Comparator.comparing(
                        (Song s) -> snap.summary(s).getVoteCount(), Comparator.reverseOrder());
                Comparator<Song> byAverage = Comparator.comparing(
                        (Song s) -> Objects.requireNonNullElse(snap.summary(s).getScoreAverage(), 0.0),
                        Comparator.reverseOrder());
                yield (snap.voteMethod() == VoteMethod.SCORE ? byAverage.thenComparing(byCount) : byCount)
                        .thenComparing(recent);
            }
        };
    }

    private SongViews.Card card(
            Song s,
            EvaluationSnapshot snap,
            Map<Long, List<SongSessionMember>> sessions,
            Map<Long, List<SongPartRequirement>> requirements,
            Long me) {
        boolean confirmed = s.isConfirmed();
        List<SongSessionMember> songSessions = sessions.getOrDefault(s.getId(), List.of());
        return SongViews.Card.builder()
                .song(SongResponse.from(s))
                .evaluation(snap.summary(s))
                .sessionCount(confirmed ? songSessions.size() : null)
                .requirementsSatisfied(
                        confirmed ? SongSupport.requirementsSatisfied(requirements.getOrDefault(s.getId(), List.of()), songSessions) : null)
                .myPlayability(
                        me == null
                                ? List.of()
                                : snap.playsOf(s.getId(), me).stream()
                                        .map(p -> SongViews.MyPlayability.builder()
                                                .partId(p.getPart().getId())
                                                .partName(p.getPart().getName())
                                                .answer(p.getAnswer())
                                                .reason(p.getReason())
                                                .build())
                                        .toList())
                .myVote(
                        me == null
                                ? null
                                : snap.voteOf(s.getId(), me)
                                        .map(v -> SongViews.MyVote.builder()
                                                .score(v.getScore())
                                                .build())
                                        .orElse(null))
                .build();
    }

    // ---------- API-077 곡 상세 ----------

    public SongViews.Detail detail(PerformanceRef ref, Long songId) {
        PerformanceAccess access = accessService.get(ref);
        Song song = songSupport.getSong(access.performanceId(), songId);
        EvaluationSnapshot snap = evaluationService.snapshot(access.performanceId());
        List<SongSessionMember> sessions = songSupport.sessionsOf(songId);
        Set<Long> sessionIds =
                sessions.stream().map(s -> s.getPerformanceMember().getId()).collect(Collectors.toSet());
        if (!SongSupport.canSee(access, song, snap.teamMembers(), sessionIds)) {
            // GUEST: 다른 세트리스트 곡은 LOCKED·FINISHED에서 최소 필드만
            boolean setlistVisible = access.isGuest()
                    && song.isConfirmed()
                    && (access.status() == PerformanceStatus.LOCKED || access.status() == PerformanceStatus.FINISHED);
            if (!setlistVisible) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
            }
            return SongViews.Detail.builder()
                    .song(SongResponse.minimal(song))
                    .sessions(List.of())
                    .requirements(List.of())
                    .schedules(List.of())
                    .auditions(List.of())
                    .build();
        }
        Long me = access.myMemberId();
        return SongViews.Detail.builder()
                .song(SongResponse.from(song))
                .sessions(sessionViews(song, sessions))
                .requirements(requirementRepository.findBySongId(songId).stream()
                        .map(SongViews.Requirement::of)
                        .toList())
                // TENTATIVE(임시) 일정은 임원만
                .schedules(scheduleLinkService.schedulesOf(songId).stream()
                        .filter(s -> access.isAdmin() || s.getStatus() != ScheduleStatus.TENTATIVE)
                        .map(SongViews.Schedule::of)
                        .toList())
                // A-09: A는 전체, 일반 사용자는 본인이 지원자인 세션 심사만
                .auditions(auditionQueryService.entriesOf(songId).stream()
                        .filter(a -> access.isAdmin()
                                || (a.getPerformanceMember() != null
                                        && a.getPerformanceMember().getId().equals(me)))
                        .map(SongViews.Audition::of)
                        .toList())
                .evaluation(snap.summary(song))
                .build();
    }

    private List<SongViews.Session> sessionViews(Song song, List<SongSessionMember> sessions) {
        Set<Long> teamMembers = song.getAssignedTeam() == null
                ? Set.of()
                : teamService.memberIdsByTeam(song.getPerformance().getId())
                        .getOrDefault(song.getAssignedTeam().getId(), Set.of());
        return sessions.stream()
                .map(s -> SongViews.Session.builder()
                        .id(s.getId())
                        .performanceMemberId(s.getPerformanceMember().getId())
                        .membershipId(s.getPerformanceMember().getMembership().getId())
                        .displayName(PerformanceAccessService.displayName(s.getPerformanceMember()))
                        .partId(s.getPart().getId())
                        .partName(s.getPart().getName())
                        .isSupport(song.getAssignedTeam() != null
                                && !teamMembers.contains(s.getPerformanceMember().getId()))
                        .isGuest(s.getPerformanceMember().getMembership().getMemberType() == MemberType.GUEST)
                        .build())
                .toList();
    }

    // ---------- API-076 후보곡 등록·직접 확정 ----------

    @Transactional
    public SongViews.Result create(PerformanceRef ref, SongRequests.Create req) {
        // 1인 한도(S-03)·순서(X-03) 검사 전 공연 행 잠금(0.4)
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireEditable();
        boolean direct = req.getStatus() == SongStatus.CONFIRMED;
        if (req.getStatus() != null && req.getStatus() != SongStatus.PROPOSED && !direct) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_STATUS", "PROPOSED 또는 CONFIRMED만 등록할 수 있습니다.");
        }
        if (direct) {
            access.requireAdmin();
        } else if (access.isLocked()) {
            throw BusinessException.invalidState("확정된 공연에는 후보곡을 등록할 수 없습니다.");
        }
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        if (!access.isAdmin()) {
            access.requireParticipant();
            PerformanceAccess.requireOpen(policy.getCandidateState(), policy.getCandidateDeadline(), "CANDIDATE_CLOSED");
        }
        String url = requireHttps(req.getReferenceUrl());

        Team candidateTeam = null;
        if (req.getCandidateTeamId() != null) {
            candidateTeam = teamService.getTeam(access.performanceId(), req.getCandidateTeamId());
            if (candidateTeam.getStatus() == TeamStatus.REJECTED || candidateTeam.getStatus() == TeamStatus.WITHDRAWN) {
                throw BusinessException.invalidState("반려되었거나 철회된 팀입니다.");
            }
            if (!access.isAdmin() && !teamService.isMember(candidateTeam.getId(), access.myMemberId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "팀원만 팀 후보곡을 등록할 수 있습니다.");
            }
        } else if (policy.getCandidateScope() == CandidateScope.TEAM_MEMBERS && !access.isAdmin()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "TEAM_REQUIRED", "팀 범위 후보곡만 등록할 수 있습니다.");
        }

        // S-03: 한도 계산에서 임원 직접 등록 곡과 철회 곡 제외
        Integer limit = policy.getCandidateLimitPerMember();
        if (!access.isAdmin()
                && limit != null
                && songRepository.countByPerformanceIdAndProposedByIdAndStatusNot(
                                access.performanceId(), access.getMembership().getId(), SongStatus.WITHDRAWN)
                        >= limit) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "LIMIT_EXCEEDED", "1인 후보곡 등록 수(" + limit + "곡)를 넘었습니다.");
        }

        String title = req.getTitle().strip();
        String artist = blankToNull(req.getArtist());
        List<Warning> warnings = new ArrayList<>();
        if (songRepository.existsByPerformanceIdAndTitleAndArtistAndStatusNot(
                access.performanceId(), title, artist, SongStatus.WITHDRAWN)) {
            warnings.add(Warning.builder().type("DUPLICATE_SONG").build());
        }
        Song song = songRepository.saveAndFlush(Song.propose(
                access.getPerformance(), candidateTeam, title, artist, url, req.getMemo(), access.getMembership()));
        if (direct) {
            warnings.addAll(applyConfirm(access, song, req));
        }
        return result(song, warnings, null);
    }

    // ---------- API-078 기본 정보 수정 ----------

    @Transactional
    public SongViews.Result update(PerformanceRef ref, Long songId, SongRequests.Update req) {
        PerformanceAccess access = admin(accessService.get(ref));
        access.requireStatus(PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (!req.hasTitle() && !req.hasArtist() && !req.hasReferenceUrl() && !req.hasMemo()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "NO_FIELDS", "바꿀 항목이 없습니다.");
        }
        String title = song.getTitle();
        if (req.hasTitle()) {
            if (req.getTitle() == null || req.getTitle().isBlank() || req.getTitle().strip().length() > 200) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_TITLE", "제목은 1~200자입니다.");
            }
            title = req.getTitle().strip();
        }
        String artist = req.hasArtist() ? limit(blankToNull(req.getArtist()), 200, "INVALID_ARTIST") : song.getArtist();
        String url = req.hasReferenceUrl() ? requireHttps(req.getReferenceUrl()) : song.getReferenceUrl();
        String memo = req.hasMemo() ? limit(blankToNull(req.getMemo()), 500, "INVALID_MEMO") : song.getMemo();
        song.update(title, artist, url, memo);
        return result(song, List.of(), null);
    }

    // ---------- API-079 필요 편성 ----------

    @Transactional
    public SongViews.Result replaceRequirements(PerformanceRef ref, Long songId, SongRequests.Requirements req) {
        PerformanceAccess access = admin(accessService.get(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        Set<Long> seen = new HashSet<>();
        for (SongRequests.Requirement r : req.getRequirements()) {
            if (!seen.add(r.getPartId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "DUPLICATE_PART", "같은 파트가 중복되었습니다.");
            }
        }
        Map<Long, Part> parts = accessService.getParts(access.clubId(), seen);
        requirementRepository.deleteBySongId(songId);
        requirementRepository.saveAll(req.getRequirements().stream()
                .map(r -> SongPartRequirement.of(song, parts.get(r.getPartId()), r.getRequiredCount()))
                .toList());
        forceIncrement(song);
        return SongViews.Result.builder()
                .song(respond(song))
                .requirements(requirementRepository.findBySongId(songId).stream()
                        .map(SongViews.Requirement::of)
                        .toList())
                .warnings(List.of())
                .build();
    }

    // ---------- API-080 확정 ----------

    @Transactional
    public SongViews.Result confirm(PerformanceRef ref, Long songId, SongRequests.Confirm req) {
        PerformanceAccess access = admin(accessService.getForUpdate(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (song.getStatus() != SongStatus.PROPOSED) {
            throw BusinessException.invalidState("후보곡만 확정할 수 있습니다.");
        }
        return result(song, applyConfirm(access, song, req), null);
    }

    /**
     * 확정 공통(076 직접 확정, 080). DRAFT·ACTIVE는 순서·담당 팀·연주자·사유 모두 선택(S-08).
     * LOCKED는 네 값 모두 필수, 입력 순서에 끼워 넣고 1부터 다시 매김
     */
    private List<Warning> applyConfirm(PerformanceAccess access, Song song, SongRequests.ConfirmFields f) {
        if (access.isLocked()) {
            access.requireReasonIfLocked(f.getReason());
            if (f.getSetlistOrder() == null
                    || f.getAssignedTeamId() == null
                    || f.getSessions() == null
                    || f.getSessions().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.RULE_VIOLATION, "LOCKED_INPUT_INCOMPLETE", "확정된 공연에서는 순서·담당 팀·연주자를 모두 입력해야 합니다.");
            }
        }
        Team team = resolveAssignedTeam(access, song, f);
        Map<Long, Integer> before = confirmedOrders(access.performanceId());
        Integer order = f.getSetlistOrder();
        if (!access.isLocked() && order != null && before.containsValue(order)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "SETLIST_ORDER_TAKEN", "이미 사용 중인 세트리스트 순서입니다.");
        }
        song.confirm(team, access.isLocked() ? null : order, blankToNull(f.getReason()));
        songRepository.flush();

        List<Warning> warnings = List.of();
        if (f.getSessions() != null) {
            warnings = replaceSessionRows(access, song, f.getSessions(), null);
        }
        if (access.isLocked()) {
            applyOrders(access.performanceId(), insertAt(orderedIds(access.performanceId(), song.getId()), song.getId(), order));
            verifyLockRequirements(access.performanceId());
            auditLocked(access, song, f.getReason(), before);
        }
        audit(access, "SONG_DECIDED", song, f.getReason(), "{\"from\":\"PROPOSED\",\"to\":\"CONFIRMED\"}");
        return warnings;
    }

    // 생략 = 후보 팀이 APPROVED면 후보 팀, 아니면 NULL. NULL 명시 = 담당 팀 없음
    private Team resolveAssignedTeam(PerformanceAccess access, Song song, SongRequests.ConfirmFields f) {
        if (f.hasAssignedTeamId()) {
            return f.getAssignedTeamId() == null
                    ? null
                    : teamService.getApprovedTeam(access.performanceId(), f.getAssignedTeamId());
        }
        Team candidate = song.getCandidateTeam();
        return candidate != null && candidate.getStatus() == TeamStatus.APPROVED ? candidate : null;
    }

    // ---------- API-081 확정 취소 ----------

    @Transactional
    public SongViews.Result unconfirm(PerformanceRef ref, Long songId, SongRequests.Decision req) {
        PerformanceAccess access = admin(accessService.getForUpdate(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (!song.isConfirmed()) {
            throw BusinessException.invalidState("선정곡만 확정을 취소할 수 있습니다.");
        }
        access.requireReasonIfLocked(req.getReason());
        Map<Long, Integer> before = confirmedOrders(access.performanceId());
        song.unconfirm(blankToNull(req.getDecisionNote()));
        // 편성 행은 유지(집계는 CONFIRMED만이라 자동 제외)
        List<Long> released = scheduleLinkService.releaseUpcoming(songId);
        if (req.isResetEvaluations()) {
            evaluationService.deleteForSong(songId);
            audit(access, "EVALUATIONS_RESET", song, req.getReason(), null);
        }
        afterLeavingConfirmed(access, song, req.getReason(), before);
        audit(access, "SONG_DECIDED", song, req.getReason(), "{\"from\":\"CONFIRMED\",\"to\":\"PROPOSED\"}");
        return result(song, List.of(), Effects.builder().affectedScheduleIds(released).build());
    }

    // ---------- API-082 제외 ----------

    @Transactional
    public SongViews.Result reject(PerformanceRef ref, Long songId, SongRequests.Decision req) {
        PerformanceAccess access = admin(accessService.getForUpdate(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        SongStatus from = song.getStatus();
        if (from != SongStatus.PROPOSED && from != SongStatus.CONFIRMED) {
            throw BusinessException.invalidState("후보곡이나 선정곡만 제외할 수 있습니다.");
        }
        access.requireReasonIfLocked(req.getReason());
        // 8.1, 0.1: 선정곡 제외는 확인 필요
        if (from == SongStatus.CONFIRMED && !req.isConfirmed()) {
            List<Warning> warnings = new ArrayList<>();
            warnings.add(Warning.builder()
                    .type("ASSIGNMENT_RELEASED")
                    .teamId(song.getAssignedTeam() == null ? null : song.getAssignedTeam().getId())
                    .setlistOrder(song.getSetlistOrder())
                    .build());
            List<Long> upcoming = scheduleLinkService.upcomingLinks(songId);
            if (!upcoming.isEmpty()) {
                warnings.add(Warning.builder()
                        .type("SCHEDULE_LINK_RELEASED")
                        .scheduleIds(upcoming)
                        .build());
            }
            throw new BusinessException(
                    ErrorCode.CONFIRMATION_REQUIRED,
                    "CONFIRMED_SONG_REJECTION",
                    "선정곡을 제외하면 담당 팀·순서가 해제됩니다.",
                    warnings);
        }
        Map<Long, Integer> before = confirmedOrders(access.performanceId());
        song.reject(blankToNull(req.getDecisionNote()));
        // S-10. 기존 가불·투표 응답은 보존
        List<Long> released = scheduleLinkService.releaseUpcoming(songId);
        if (from == SongStatus.CONFIRMED) {
            afterLeavingConfirmed(access, song, req.getReason(), before);
        }
        audit(access, "SONG_DECIDED", song, req.getReason(), "{\"from\":\"" + from + "\",\"to\":\"REJECTED\"}");
        return result(song, List.of(), Effects.builder().affectedScheduleIds(released).build());
    }

    // LOCKED: 남은 선정곡 순서를 1부터 다시 매기고(X-06) 감사 기록(X-07)
    private void afterLeavingConfirmed(
            PerformanceAccess access, Song song, String reason, Map<Long, Integer> before) {
        songRepository.flush();
        if (access.isLocked()) {
            applyOrders(access.performanceId(), renumber(orderedIds(access.performanceId(), null)));
            auditLocked(access, song, reason, before);
        }
    }

    // ---------- API-083 제안자 철회 / 157 임원 삭제 / 156 재검토 ----------

    // 제안자 본인만. 공연 ACTIVE, 곡 PROPOSED, 후보 OPEN·마감 전
    @Transactional
    public SongViews.Result withdraw(PerformanceRef ref, Long songId, SongRequests.Decision req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        access.requireStatus(PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (!song.getProposedBy().getId().equals(access.getMembership().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "제안자만 철회할 수 있습니다.");
        }
        PerformancePolicy policy = accessService.getPolicy(access.performanceId());
        PerformanceAccess.requireOpen(policy.getCandidateState(), policy.getCandidateDeadline(), "CANDIDATE_CLOSED");
        song.withdraw(WithdrawalType.PROPOSER);
        return result(song, List.of(), null);
    }

    // S-11: 실제 삭제가 아니라 WITHDRAWN(ADMIN). 1인 한도에서 제외(S-03)
    @Transactional
    public SongViews.Result remove(PerformanceRef ref, Long songId, SongRequests.Decision req) {
        PerformanceAccess access = admin(accessService.get(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        song.withdraw(WithdrawalType.ADMIN);
        audit(access, "SONG_DECIDED", song, req.getReason(), "{\"from\":\"PROPOSED\",\"to\":\"WITHDRAWN\",\"type\":\"ADMIN\"}");
        return result(song, List.of(), null);
    }

    // REJECTED → PROPOSED. 후보곡 단계(DRAFT·ACTIVE)에서만. 이전 제외 사유는 감사 기록에 남김
    @Transactional
    public SongViews.Result reconsider(PerformanceRef ref, Long songId, SongRequests.Decision req) {
        PerformanceAccess access = admin(accessService.get(ref));
        access.requireStatus(PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        String previousNote = song.getDecisionNote();
        song.reconsider();
        audit(
                access,
                "SONG_DECIDED",
                song,
                req.getReason(),
                "{\"from\":\"REJECTED\",\"to\":\"PROPOSED\",\"previousDecisionNoteKept\":" + (previousNote != null) + "}");
        return result(song, List.of(), null);
    }

    // ---------- API-084 담당 팀 ----------

    @Transactional
    public SongViews.Result assignTeam(PerformanceRef ref, Long songId, SongRequests.AssignTeam req) {
        PerformanceAccess access = admin(accessService.get(ref));
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (!song.isConfirmed()) {
            throw BusinessException.invalidState("선정곡만 담당 팀을 정할 수 있습니다.");
        }
        access.requireReasonIfLocked(req.getReason());
        if (req.getAssignedTeamId() == null && access.isLocked()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "LOCK_REQUIREMENTS_FAILED", "확정된 공연에서는 담당 팀을 해제할 수 없습니다.");
        }
        Long before = song.getAssignedTeam() == null ? null : song.getAssignedTeam().getId();
        song.assignTeam(
                req.getAssignedTeamId() == null
                        ? null
                        : teamService.getApprovedTeam(access.performanceId(), req.getAssignedTeamId()));
        if (access.isLocked()) {
            auditLogRepository.save(AuditLog.of(
                    access.clubId(),
                    access.userId(),
                    "LOCKED_EXCEPTION_EDIT",
                    "SONG",
                    song.getId(),
                    req.getReason(),
                    "{\"assignedTeamId\":{\"from\":" + before + ",\"to\":" + req.getAssignedTeamId() + "}}"));
        }
        return result(song, List.of(), null);
    }

    // ---------- API-085 연주자 전체 저장 ----------

    /**
     * A: DRAFT·ACTIVE, LOCKED는 사유 필수. L: ACTIVE 공연의 CONFIRMED 곡, 담당 팀이 APPROVED이고 그 팀장일 때
     * 자기 팀 팀원 행만 바꾸며 기존 지원 세션(팀원이 아닌 연주자)은 그대로 보존해야 함
     */
    @Transactional
    public SongViews.Result replaceSessions(PerformanceRef ref, Long songId, SongRequests.Sessions req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        Song song = songSupport.getSong(access.performanceId(), songId);
        song.verifyVersion(req.getVersion());
        if (song.getStatus() != SongStatus.PROPOSED && song.getStatus() != SongStatus.CONFIRMED) {
            throw BusinessException.invalidState("후보곡이나 선정곡만 연주자를 정할 수 있습니다.");
        }
        Set<Long> teamOnly = null;
        if (access.isAdmin()) {
            access.requireReasonIfLocked(req.getReason());
        } else {
            access.requireStatus(PerformanceStatus.ACTIVE);
            Team team = song.getAssignedTeam();
            if (!song.isConfirmed()
                    || team == null
                    || team.getStatus() != TeamStatus.APPROVED
                    || !team.isLeader(access.myMemberId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "담당 팀 팀장만 연주자를 바꿀 수 있습니다.");
            }
            teamOnly = teamService.memberIdsByTeam(access.performanceId()).getOrDefault(team.getId(), Set.of());
        }
        Map<Long, Integer> before = access.isLocked() ? confirmedOrders(access.performanceId()) : Map.of();
        List<Warning> warnings = replaceSessionRows(access, song, req.getSessions(), teamOnly);
        forceIncrement(song);
        if (access.isLocked() && song.isConfirmed()) {
            songRepository.flush();
            verifyLockRequirements(access.performanceId());
            auditLocked(access, song, req.getReason(), before);
        }
        return SongViews.Result.builder()
                .song(respond(song))
                .sessions(sessionViews(song, songSupport.sessionsOf(songId)))
                .warnings(warnings)
                .build();
    }

    /**
     * 연주자 행 원자적 교체. 중복 (사람, 파트) 거부, 활성 파트만(M-02), 선정곡은 1명 이상.
     * LOCKED 선정곡은 연주자 전원 APPROVED·유효 소속. teamOnly가 있으면(L) 그 밖의 기존 행은 그대로여야 함
     */
    private List<Warning> replaceSessionRows(
            PerformanceAccess access, Song song, List<SongRequests.Session> items, Set<Long> teamOnly) {
        Set<String> seen = new HashSet<>();
        for (SongRequests.Session m : items) {
            if (!seen.add(m.getPerformanceMemberId() + ":" + m.getPartId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "DUPLICATE_SESSION", "같은 연주자·파트가 중복되었습니다.");
            }
        }
        if (song.isConfirmed() && items.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "SESSIONS_REQUIRED", "선정곡에는 연주자가 1명 이상 있어야 합니다.");
        }
        if (teamOnly != null) {
            Set<String> keptSupport = songSupport.sessionsOf(song.getId()).stream()
                    .filter(s -> !teamOnly.contains(s.getPerformanceMember().getId()))
                    .map(s -> s.getPerformanceMember().getId() + ":" + s.getPart().getId())
                    .collect(Collectors.toSet());
            Set<String> requestedSupport = items.stream()
                    .filter(m -> !teamOnly.contains(m.getPerformanceMemberId()))
                    .map(m -> m.getPerformanceMemberId() + ":" + m.getPartId())
                    .collect(Collectors.toSet());
            if (!keptSupport.equals(requestedSupport)) {
                throw new BusinessException(
                        ErrorCode.FORBIDDEN, "SUPPORT_SESSION_LOCKED", "팀장은 자기 팀 팀원의 연주자 행만 바꿀 수 있습니다.");
            }
        }
        Long pid = access.performanceId();
        Map<Long, PerformanceMember> pms = accessService.getMembers(
                pid, items.stream().map(SongRequests.Session::getPerformanceMemberId).collect(Collectors.toSet()));
        Map<Long, Part> parts = accessService.getActiveParts(
                access.clubId(), items.stream().map(SongRequests.Session::getPartId).collect(Collectors.toSet()));
        boolean strict = access.isLocked() && song.isConfirmed();
        for (PerformanceMember pm : pms.values()) {
            boolean ok = strict
                    ? pm.getStatus() == PerformanceMemberStatus.APPROVED
                            && VALID_MEMBERSHIP.contains(pm.getMembership().getStatus())
                    : pm.isActiveParticipant();
            if (!ok) {
                throw new BusinessException(ErrorCode.RULE_VIOLATION, "SESSION_MEMBER_INVALID", "연주자로 지정할 수 없는 참여자가 있습니다.");
            }
        }
        sessionMemberRepository.deleteBySongId(song.getId());
        sessionMemberRepository.saveAllAndFlush(items.stream()
                .map(m -> SongSessionMember.of(song, pms.get(m.getPerformanceMemberId()), parts.get(m.getPartId())))
                .toList());
        return sessionWarnings(access, song);
    }

    // M-04 필요 편성 부족·초과, M-05 1인 권장 출연 곡 수 초과(경고만, 거절하지 않음)
    private List<Warning> sessionWarnings(PerformanceAccess access, Song song) {
        List<Warning> warnings = new ArrayList<>();
        List<SongSessionMember> sessions = songSupport.sessionsOf(song.getId());
        Map<Long, Long> assigned = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getPart().getId(), Collectors.counting()));
        for (SongPartRequirement r : requirementRepository.findBySongId(song.getId())) {
            int count = assigned.getOrDefault(r.getPart().getId(), 0L).intValue();
            if (count != r.getRequiredCount()) {
                warnings.add(Warning.builder()
                        .type(count < r.getRequiredCount() ? "PART_SHORTAGE" : "PART_EXCESS")
                        .partId(r.getPart().getId())
                        .partName(r.getPart().getName())
                        .requiredCount(r.getRequiredCount())
                        .assignedCount(count)
                        .build());
            }
        }
        Integer max = accessService.getPolicy(access.performanceId()).getMaxSongsPerMember();
        if (max != null) {
            Map<Long, Set<Long>> songsByMember = new HashMap<>();
            songSupport.sessionsOfPerformance(access.performanceId()).stream()
                    .filter(s -> s.getSong().isConfirmed() || s.getSong().getId().equals(song.getId()))
                    .forEach(s -> songsByMember
                            .computeIfAbsent(s.getPerformanceMember().getId(), k -> new HashSet<>())
                            .add(s.getSong().getId()));
            Map<Long, PerformanceMember> mine = new LinkedHashMap<>();
            sessions.forEach(s -> mine.putIfAbsent(s.getPerformanceMember().getId(), s.getPerformanceMember()));
            mine.forEach((id, pm) -> {
                int count = songsByMember.getOrDefault(id, Set.of()).size();
                if (count > max) {
                    warnings.add(Warning.builder()
                            .type("SONG_COUNT_EXCEEDED")
                            .performanceMemberId(id)
                            .displayName(PerformanceAccessService.displayName(pm))
                            .songCount(count)
                            .build());
                }
            });
        }
        return warnings;
    }

    // ---------- API-086 연주자로 팀 만들기 ----------

    @Transactional
    public TeamResults.Result createTeamFromSong(PerformanceRef ref, TeamRequests.Create req) {
        if (req.getMembers() != null && !req.getMembers().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "SOURCE_SONG_WITH_MEMBERS", "sourceSongId와 members를 함께 보낼 수 없습니다.");
        }
        PerformanceAccess access = admin(accessService.getForUpdate(ref));
        access.requireStatus(PerformanceStatus.ACTIVE);
        Song song = songSupport.getSong(access.performanceId(), req.getSourceSongId());
        if (!song.isConfirmed()) {
            throw BusinessException.invalidState("선정곡만 연주자로 팀을 만들 수 있습니다.");
        }
        if (song.getAssignedTeam() != null) {
            throw BusinessException.invalidState("이미 담당 팀이 있는 곡입니다.");
        }
        Team team = teamService.createFromSessions(
                access, song, songSupport.sessionsOf(song.getId()), req.getName(), req.getLeaderMembershipId());
        return TeamResults.Result.builder()
                .team(teamService.respond(access, team))
                .song(respond(song))
                .warnings(List.of())
                .build();
    }

    // ---------- 세트리스트(SetlistService가 사용) ----------

    public List<Song> getConfirmedSongs(Long performanceId) {
        return songSupport.getSongs(performanceId, List.of(SongStatus.CONFIRMED));
    }

    // uk_song_setlist_order 때문에 한 트랜잭션에서 NULL로 비운 뒤 재부여
    @Transactional
    public void applySetlistOrders(Map<Song, Integer> orders) {
        orders.keySet().forEach(song -> song.changeSetlistOrder(null));
        songRepository.flush();
        orders.forEach(Song::changeSetlistOrder);
        songRepository.flush();
    }

    // L-01~L-03(LOCKED 유지 조건): 모든 선정곡에 순서·담당 팀·APPROVED 연주자 1명 이상
    @Transactional
    public void verifyLockRequirements(Long performanceId) {
        Map<Long, List<SongSessionMember>> sessions = sessionsBySong(performanceId);
        for (Song s : getConfirmedSongs(performanceId)) {
            List<SongSessionMember> rows = sessions.getOrDefault(s.getId(), List.of());
            boolean ok = s.getSetlistOrder() != null
                    && s.getAssignedTeam() != null
                    && !rows.isEmpty()
                    && rows.stream().allMatch(r -> r.getPerformanceMember().getStatus() == PerformanceMemberStatus.APPROVED);
            if (!ok) {
                throw new BusinessException(
                        ErrorCode.RULE_VIOLATION, "LOCK_REQUIREMENTS_FAILED", "확정 조건(순서·담당 팀·연주자)을 만족하지 않는 선정곡이 있습니다.");
            }
        }
    }

    // ---------- 순서 계산(순수 함수) ----------

    // 배정된 선정곡 ID를 현재 순서대로. exclude는 빼고
    private List<Long> orderedIds(Long performanceId, Long exclude) {
        return getConfirmedSongs(performanceId).stream()
                .filter(s -> s.getSetlistOrder() != null && !s.getId().equals(exclude))
                .sorted(Comparator.comparing(Song::getSetlistOrder))
                .map(Song::getId)
                .toList();
    }

    /** X-05: order 위치에 끼워 넣고(그 뒤는 한 칸씩 밀림) 1부터 다시 매김. 범위를 넘으면 맨 뒤. */
    static Map<Long, Integer> insertAt(List<Long> ordered, Long songId, int order) {
        List<Long> ids = new ArrayList<>(ordered);
        ids.add(Math.min(Math.max(order, 1) - 1, ids.size()), songId);
        return renumber(ids);
    }

    // X-06: 1부터 빈틈 없이
    static Map<Long, Integer> renumber(List<Long> ordered) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            result.put(ordered.get(i), i + 1);
        }
        return result;
    }

    private void applyOrders(Long performanceId, Map<Long, Integer> orders) {
        Map<Long, Song> songs =
                getConfirmedSongs(performanceId).stream().collect(Collectors.toMap(Song::getId, s -> s));
        Map<Song, Integer> bySong = new LinkedHashMap<>();
        orders.forEach((id, order) -> bySong.put(songs.get(id), order));
        applySetlistOrders(bySong);
    }

    private Map<Long, Integer> confirmedOrders(Long performanceId) {
        Map<Long, Integer> orders = new LinkedHashMap<>();
        getConfirmedSongs(performanceId).stream()
                .filter(s -> s.getSetlistOrder() != null)
                .forEach(s -> orders.put(s.getId(), s.getSetlistOrder()));
        return orders;
    }

    // ---------- 공통 ----------

    private Map<Long, List<SongSessionMember>> sessionsBySong(Long performanceId) {
        return songSupport.sessionsOfPerformance(performanceId).stream()
                .collect(Collectors.groupingBy(s -> s.getSong().getId()));
    }

    private static Set<Long> memberIds(Map<Long, List<SongSessionMember>> sessions, Song song) {
        return sessions.getOrDefault(song.getId(), List.of()).stream()
                .map(s -> s.getPerformanceMember().getId())
                .collect(Collectors.toSet());
    }

    private static PerformanceAccess admin(PerformanceAccess access) {
        access.requireAdmin();
        access.requireEditable();
        return access;
    }

    // 규칙 0.5, S-11: https만, 500자 이하. 서버는 URL을 가져오지 않음
    static String requireHttps(String url) {
        String value = blankToNull(url);
        if (value != null && (!value.matches("^https://\\S+$") || value.length() > 500)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_URL", "https 링크만 입력할 수 있습니다.");
        }
        return value;
    }

    private static String limit(String value, int max, String reason) {
        if (value != null && value.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, reason, max + "자를 넘을 수 없습니다.");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // 하위 행(편성·연주자) 변경도 곡 version을 올림
    private void forceIncrement(Song song) {
        em.lock(song, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private SongResponse respond(Song song) {
        songRepository.flush();
        return SongResponse.from(song);
    }

    private SongViews.Result result(Song song, List<Warning> warnings, Effects effects) {
        return SongViews.Result.builder()
                .song(respond(song))
                .warnings(warnings)
                .effects(effects)
                .build();
    }

    private void audit(PerformanceAccess access, String action, Song song, String reason, String detail) {
        auditLogRepository.save(AuditLog.of(
                access.clubId(), access.userId(), action, "SONG", song.getId(), blankToNull(reason), detail));
    }

    // X-07: 사유와 변경 전·후 순서(순서가 바뀐 곡 전체)
    private void auditLocked(PerformanceAccess access, Song song, String reason, Map<Long, Integer> before) {
        Map<Long, Integer> after = confirmedOrders(access.performanceId());
        Set<Long> ids = new HashSet<>(before.keySet());
        ids.addAll(after.keySet());
        String changes = ids.stream()
                .filter(id -> !Objects.equals(before.get(id), after.get(id)))
                .sorted()
                .map(id -> "{\"songId\":" + id + ",\"from\":" + before.get(id) + ",\"to\":" + after.get(id) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        audit(access, "LOCKED_EXCEPTION_EDIT", song, reason, "{\"status\":\"" + song.getStatus() + "\",\"orders\":" + changes + "}");
    }
}
