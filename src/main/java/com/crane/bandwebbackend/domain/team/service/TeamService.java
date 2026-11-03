package com.crane.bandwebbackend.domain.team.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.dto.TeamMemberResponse;
import com.crane.bandwebbackend.domain.team.dto.TeamRequests;
import com.crane.bandwebbackend.domain.team.dto.TeamResponse;
import com.crane.bandwebbackend.domain.team.dto.TeamResults;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.team.entity.TeamMember;
import com.crane.bandwebbackend.domain.team.entity.TeamSource;
import com.crane.bandwebbackend.domain.team.entity.TeamStatus;
import com.crane.bandwebbackend.domain.team.repository.TeamMemberRepository;
import com.crane.bandwebbackend.domain.team.repository.TeamRepository;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.audit.AuditLogRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.Effects;
import com.crane.bandwebbackend.global.response.Warning;
import com.crane.bandwebbackend.global.time.Kst;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 편성·상태 전이(API-062~074, 154, 155). L = 팀장, A = 임원, P = 공연 참여자.
 * 곡 쪽 연쇄 처리는 SongSupport로(SongService가 이 클래스에 의존하므로 순환 방지)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    // 임원이 지정할 수 있는 소속 상태
    private static final Set<MembershipStatus> ELIGIBLE =
            Set.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE, MembershipStatus.GRADUATED);
    // UI-15: 부원 신청은 ACTIVE·ON_LEAVE 정회원만. 졸업·객원은 임원이 지정
    private static final Set<MembershipStatus> SELF_ELIGIBLE =
            Set.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE);

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PerformanceAccessService accessService;
    private final SongSupport songSupport;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager em;

    // ---------- 조회 도우미 ----------

    public Team getTeam(Long performanceId, Long teamId) {
        return teamRepository
                .findByIdAndPerformanceId(teamId, performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다."));
    }

    // 곡 담당 팀은 승인된 팀만
    public Team getApprovedTeam(Long performanceId, Long teamId) {
        Team team = getTeam(performanceId, teamId);
        if (team.getStatus() != TeamStatus.APPROVED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "TEAM_NOT_APPROVED", "승인된 팀만 지정할 수 있습니다.");
        }
        return team;
    }

    public List<Team> getTeams(Long performanceId) {
        return teamRepository.findAllWithLeader(performanceId);
    }

    public boolean isMember(Long teamId, Long performanceMemberId) {
        return performanceMemberId != null
                && teamMemberRepository.existsByTeamIdAndPerformanceMemberId(teamId, performanceMemberId);
    }

    public List<TeamMember> membersOf(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId);
    }

    public List<TeamMember> membersOfPerformance(Long performanceId) {
        return teamMemberRepository.findByPerformanceId(performanceId);
    }

    // 팀 ID → 팀원 performance_member ID
    public Map<Long, Set<Long>> memberIdsByTeam(Long performanceId) {
        return membersOfPerformance(performanceId).stream()
                .collect(Collectors.groupingBy(
                        m -> m.getTeam().getId(),
                        Collectors.mapping(m -> m.getPerformanceMember().getId(), Collectors.toSet())));
    }

    // UI-9: APPROVED는 공개, 나머지는 A와 팀원. GUEST는 자기 팀만
    public static boolean canSee(PerformanceAccess access, Team team, Set<Long> memberIds) {
        if (access.isAdmin()) {
            return true;
        }
        boolean mine = access.myMemberId() != null && memberIds.contains(access.myMemberId());
        if (access.isGuest()) {
            return mine;
        }
        return mine || team.getStatus() == TeamStatus.APPROVED;
    }

    public TeamResponse toResponse(
            PerformanceAccess access, Team team, List<TeamMember> members, long assigned, long candidates) {
        boolean mine = access.myMemberId() != null
                && members.stream()
                        .anyMatch(m -> m.getPerformanceMember().getId().equals(access.myMemberId()));
        boolean privileged = access.isAdmin() || mine;
        PerformanceMember leader = team.getLeader();
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .status(team.getStatus())
                .source(team.getSource())
                .isDefault(team.isDefault())
                .leaderMembershipId(
                        leader == null ? null : leader.getMembership().getId())
                .leaderDisplayName(leader == null ? null : PerformanceAccessService.displayName(leader))
                .rejectReason(privileged && team.getStatus() == TeamStatus.REJECTED ? team.getRejectReason() : null)
                .memberCount(members.size())
                .partSummary(partSummary(members))
                .assignedSongCount(privileged ? (int) assigned : null)
                .candidateSongCount(privileged ? (int) candidates : null)
                .isMine(mine)
                .submittedAt(Kst.fromUtc(team.getSubmittedAt()))
                .decidedAt(Kst.fromUtc(team.getDecidedAt()))
                .createdAt(Kst.fromUtc(team.getCreatedAt()))
                .version(team.getVersion())
                .build();
    }

    // 변경 직후 한 팀 응답(버전 증가 반영)
    public TeamResponse respond(PerformanceAccess access, Team team) {
        teamRepository.flush();
        return toResponse(
                access,
                team,
                membersOf(team.getId()),
                songSupport.countAssigned(team.getId()),
                songSupport.countCandidates(team.getId()));
    }

    public List<TeamMemberResponse> toMemberResponses(PerformanceAccess access, Team team, List<TeamMember> members) {
        Map<Long, Set<Long>> registered = accessService.registeredPartIds(members.stream()
                .map(m -> m.getPerformanceMember().getMembership().getId())
                .toList());
        return members.stream()
                .map(m -> {
                    PerformanceMember pm = m.getPerformanceMember();
                    Membership ms = pm.getMembership();
                    Part part = m.getPart();
                    return TeamMemberResponse.builder()
                            .id(m.getId())
                            .membershipId(ms.getId())
                            .performanceMemberId(pm.getId())
                            .displayName(PerformanceAccessService.displayName(ms))
                            .profileImageUrl(ms.getUser()
                                    .profileImageUrlFor(access.getMembership().getUser()))
                            .isLeader(team.isLeader(pm.getId()))
                            .partId(part == null ? null : part.getId())
                            .partName(part == null ? null : part.getName())
                            .participationStatus(pm.getStatus())
                            .isRegisteredPart(part == null
                                    || registered
                                            .getOrDefault(ms.getId(), Set.of())
                                            .contains(part.getId()))
                            .build();
                })
                .toList();
    }

    // LinkedHashMap은 NULL 키 허용 → 대표 파트 없는 팀원을 한 항목으로
    private static List<TeamResponse.PartCount> partSummary(List<TeamMember> members) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (TeamMember m : members) {
            Long partId = m.getPart() == null ? null : m.getPart().getId();
            counts.merge(partId, 1, Integer::sum);
            names.put(partId, m.getPart() == null ? null : m.getPart().getName());
        }
        return counts.entrySet().stream()
                .map(e -> TeamResponse.PartCount.builder()
                        .partId(e.getKey())
                        .partName(names.get(e.getKey()))
                        .count(e.getValue())
                        .build())
                .toList();
    }

    // ---------- API-154 팀 이름 기본값 ----------

    public TeamResults.DefaultName defaultName(PerformanceRef ref) {
        accessService.get(ref);
        return TeamResults.DefaultName.builder()
                .name(nextDefaultName(teamRepository.findNamesByPerformanceId(ref.getPerformanceId())))
                .build();
    }

    // T-07: 공연의 모든 상태 팀 이름을 보고 비어 있는 가장 작은 번호 "n팀"
    static String nextDefaultName(Set<String> taken) {
        Set<String> lower = taken.stream().map(String::toLowerCase).collect(Collectors.toSet());
        int n = 1;
        while (lower.contains(n + "팀")) {
            n++;
        }
        return n + "팀";
    }

    // ---------- API-062 팀 생성 ----------

    @Transactional
    public TeamResults.Result create(PerformanceRef ref, TeamRequests.Create req) {
        // 이름 중복 검사 전 공연 행 잠금(0.4)
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireEditable();
        if (access.isAdmin()) {
            access.requireStatus(PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE);
        } else {
            access.requireStatus(PerformanceStatus.ACTIVE);
            PerformancePolicy policy = accessService.getPolicy(access.performanceId());
            PerformanceAccess.requireOpen(
                    policy.getTeamSubmissionState(), policy.getTeamSubmissionDeadline(), "TEAM_SUBMISSION_CLOSED");
        }
        String name = requireName(req.getName());
        requireUniqueName(access.performanceId(), name, null);

        // membershipId → partId(선택). P가 만들면 본인이 자동으로 팀원
        Map<Long, Long> requested = new LinkedHashMap<>();
        if (!access.isAdmin()) {
            requested.put(access.getMembership().getId(), null);
        }
        for (TeamRequests.Member m : req.getMembers() == null ? List.<TeamRequests.Member>of() : req.getMembers()) {
            if (requested.containsKey(m.getMembershipId())
                    && !m.getMembershipId().equals(access.getMembership().getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "DUPLICATE_MEMBER", "같은 부원이 중복되었습니다.");
            }
            requested.put(m.getMembershipId(), m.getPartId());
        }
        Map<Long, Part> parts = accessService.getActiveParts(
                access.clubId(),
                requested.values().stream().filter(Objects::nonNull).collect(Collectors.toSet()));

        Long leaderMembershipId = access.isAdmin()
                ? req.getLeaderMembershipId()
                : access.getMembership().getId();
        if (leaderMembershipId != null && !requested.containsKey(leaderMembershipId)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "LEADER_NOT_TEAM_MEMBER", "팀장은 팀원 중에서 지정해야 합니다.");
        }

        Team team = access.isAdmin()
                ? Team.createApproved(
                        access.getPerformance(),
                        name,
                        TeamSource.ADMIN,
                        access.getMembership().getUser())
                : Team.createDraft(
                        access.getPerformance(), name, access.getMembership().getUser());
        // 팀장 FK(fk_team_leader_is_member) 때문에 팀 → 팀원 → 팀장 순서로 저장
        teamRepository.saveAndFlush(team);
        List<TeamMember> members = new ArrayList<>();
        PerformanceMember leader = null;
        for (Map.Entry<Long, Long> e : requested.entrySet()) {
            Membership membership = accessService.getMembership(access.clubId(), e.getKey());
            requireEligible(access, membership);
            PerformanceMember pm = enroll(access, membership, access.isAdmin());
            members.add(TeamMember.of(team, pm, e.getValue() == null ? null : parts.get(e.getValue())));
            if (e.getKey().equals(leaderMembershipId)) {
                leader = pm;
            }
        }
        teamMemberRepository.saveAllAndFlush(members);
        if (leader != null) {
            team.changeLeader(leader);
        }
        return result(access, team, partWarnings(members));
    }

    // ---------- API-064 팀명 변경 ----------

    @Transactional
    public TeamResults.Result rename(PerformanceRef ref, Long teamId, TeamRequests.Rename req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireEditable();
        Team team = getTeam(access.performanceId(), teamId);
        team.verifyVersion(req.getVersion());
        if (access.isAdmin()) {
            access.requireReasonIfLocked(req.getReason());
        } else {
            requireLeader(access, team);
            if (team.getStatus() == TeamStatus.APPROVED) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "승인된 팀의 이름은 임원만 바꿀 수 있습니다.");
            }
            access.requireStatus(PerformanceStatus.ACTIVE);
            requireTeamStatus(team, TeamStatus.DRAFT);
        }
        String name = requireName(req.getName());
        requireUniqueName(access.performanceId(), name, team);
        team.rename(name);
        auditIfLocked(access, team, req.getReason(), "{\"field\":\"name\"}");
        return result(access, team, List.of());
    }

    // ---------- API-065 팀원 추가 ----------

    @Transactional
    public TeamResults.Result addMember(PerformanceRef ref, Long teamId, TeamRequests.AddMember req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        Team team = getTeam(access.performanceId(), teamId);
        team.verifyVersion(req.getVersion());
        requireMemberEditable(access, team, req.getReason());

        Membership membership = accessService.getMembership(access.clubId(), req.getMembershipId());
        requireEligible(access, membership);
        PerformanceMember existing = accessService
                .findMemberByMembership(access.performanceId(), membership.getId())
                .orElse(null);
        if (existing != null && isMember(teamId, existing.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "DUPLICATE_TEAM_MEMBER", "이미 팀원입니다.");
        }
        // LOCKED 공연에서는 APPROVED 참여자만
        if (access.isLocked() && (existing == null || existing.getStatus() != PerformanceMemberStatus.APPROVED)) {
            throw new BusinessException(
                    ErrorCode.RULE_VIOLATION, "PARTICIPANT_NOT_APPROVED", "확정된 공연에는 승인된 참여자만 추가할 수 있습니다.");
        }
        Part part = req.getPartId() == null ? null : accessService.getActivePart(access.clubId(), req.getPartId());
        // ponytail: 명세는 "미참여자는 APPLIED로 생성". 임원이 승인 팀에 넣을 때는 062와 같이 APPROVED로 처리(L-05 차단 방지)
        PerformanceMember pm = enroll(access, membership, access.isAdmin() && team.getStatus() == TeamStatus.APPROVED);
        TeamMember member = teamMemberRepository.save(TeamMember.of(team, pm, part));
        forceIncrement(team);
        auditIfLocked(access, team, req.getReason(), "{\"addedPerformanceMemberId\":" + pm.getId() + "}");
        return result(access, team, partWarnings(List.of(member)));
    }

    // ---------- API-066 대표 파트 변경 ----------

    @Transactional
    public TeamResults.Result changeMemberPart(
            PerformanceRef ref, Long teamId, Long teamMemberId, TeamRequests.ChangePart req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        Team team = getTeam(access.performanceId(), teamId);
        team.verifyVersion(req.getVersion());
        requireMemberEditable(access, team, req.getReason());
        TeamMember member = getTeamMember(team, teamMemberId);
        member.changePart(
                req.getPartId() == null ? null : accessService.getActivePart(access.clubId(), req.getPartId()));
        forceIncrement(team);
        return result(access, team, partWarnings(List.of(member)));
    }

    // ---------- API-067 팀원 제외 ----------

    @Transactional
    public TeamResults.Result removeMember(
            PerformanceRef ref, Long teamId, Long teamMemberId, TeamRequests.RemoveMember req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        Team team = getTeam(access.performanceId(), teamId);
        team.verifyVersion(req.getVersion());
        requireMemberEditable(access, team, req.getReason());
        TeamMember member = getTeamMember(team, teamMemberId);
        Long pmId = member.getPerformanceMember().getId();
        if (team.isLeader(pmId)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "LEADER_CHANGE_REQUIRED", "팀장을 먼저 교체해 주세요.");
        }
        if (req.isRemoveSessions()) {
            // 담당 팀 선정곡에서 그 사람의 연주자 행 제거. 마지막 연주자가 없어지는 곡이 있으면 거절
            Map<Song, List<SongSessionMember>> bySong =
                    songSupport.sessionsOfPerformance(access.performanceId()).stream()
                            .filter(s -> s.getSong().getStatus() == SongStatus.CONFIRMED
                                    && s.getSong().getAssignedTeam() != null
                                    && s.getSong().getAssignedTeam().getId().equals(teamId))
                            .collect(Collectors.groupingBy(SongSessionMember::getSong));
            List<Long> songIds = new ArrayList<>();
            bySong.forEach((song, sessions) -> {
                boolean has = sessions.stream()
                        .anyMatch(s -> s.getPerformanceMember().getId().equals(pmId));
                if (!has) {
                    return;
                }
                if (sessions.stream()
                        .allMatch(s -> s.getPerformanceMember().getId().equals(pmId))) {
                    throw new BusinessException(
                            ErrorCode.STATE_CONFLICT, "RELATION_CONFLICT", "연주자가 한 명도 남지 않는 선정곡이 있습니다.");
                }
                songIds.add(song.getId());
            });
            songSupport.removeSessions(pmId, songIds);
        }
        teamMemberRepository.delete(member);
        forceIncrement(team);
        auditIfLocked(access, team, req.getReason(), "{\"removedPerformanceMemberId\":" + pmId + "}");
        return result(access, team, List.of());
    }

    // ---------- API-068 팀장 교체 ----------

    @Transactional
    public TeamResults.Result changeLeader(PerformanceRef ref, Long teamId, TeamRequests.Leader req) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        Team team = getTeam(access.performanceId(), teamId);
        team.verifyVersion(req.getVersion());
        if (access.isAdmin()) {
            access.requireReasonIfLocked(req.getReason());
        } else {
            requireLeader(access, team);
            access.requireStatus(PerformanceStatus.ACTIVE);
            if (req.getLeaderMembershipId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "팀장 해제는 임원만 할 수 있습니다.");
            }
        }
        PerformanceMember leader = null;
        if (req.getLeaderMembershipId() != null) {
            leader = membersOf(teamId).stream()
                    .map(TeamMember::getPerformanceMember)
                    .filter(pm -> pm.getMembership().getId().equals(req.getLeaderMembershipId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.STATE_CONFLICT, "LEADER_NOT_TEAM_MEMBER", "팀장은 팀원 중에서 지정해야 합니다."));
        }
        team.changeLeader(leader);
        auditIfLocked(access, team, req.getReason(), "{\"field\":\"leader\"}");
        return result(access, team, List.of());
    }

    // ---------- API-069~074 상태 전이 ----------

    @Transactional
    public TeamResults.Result submit(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        return transition(ref, teamId, req, false, (access, team) -> {
            if (!access.isAdmin()) {
                PerformancePolicy policy = accessService.getPolicy(access.performanceId());
                PerformanceAccess.requireOpen(
                        policy.getTeamSubmissionState(), policy.getTeamSubmissionDeadline(), "TEAM_SUBMISSION_CLOSED");
            }
            if (team.getLeader() == null) {
                throw new BusinessException(ErrorCode.RULE_VIOLATION, "LEADER_REQUIRED", "팀장을 지정해야 제출할 수 있습니다.");
            }
            if (membersOf(teamId).stream()
                    .noneMatch(m -> m.getPerformanceMember().isActiveParticipant())) {
                throw new BusinessException(ErrorCode.RULE_VIOLATION, "MEMBERS_REQUIRED", "팀원이 있어야 제출할 수 있습니다.");
            }
            team.submit();
        });
    }

    @Transactional
    public TeamResults.Result unsubmit(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        return transition(ref, teamId, req, false, (access, team) -> team.unsubmit());
    }

    // 승인 시 APPLIED 팀원도 함께 APPROVED
    @Transactional
    public TeamResults.Result approve(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        return transition(ref, teamId, req, true, (access, team) -> {
            team.approve();
            membersOf(teamId).forEach(m -> m.getPerformanceMember().approve());
        });
    }

    /**
     * API-072 반려(T-04, T-05, UI-10). 같은 트랜잭션에서
     * ① 팀 범위 후보곡 → WITHDRAWN(TEAM_WITHDRAWN) ② 이 팀 신청으로만 들어온 APPLIED 참여자 → REJECTED.
     * 팀 범위 선정곡이 있으면 확인 필요(0.1)
     */
    @Transactional
    public TeamResults.Result reject(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "REASON_REQUIRED", "반려 사유를 입력해 주세요.");
        }
        Effects[] effects = new Effects[1];
        TeamResults.Result r = transition(ref, teamId, req, true, (access, team) -> {
            if (!req.isConfirmed() && songSupport.countConfirmedInScope(teamId) > 0) {
                throw new BusinessException(
                        ErrorCode.CONFIRMATION_REQUIRED,
                        "CONFIRMED_SONGS_IN_TEAM_SCOPE",
                        "이 팀 범위의 선정곡이 있습니다. 확인 후 다시 요청해 주세요.",
                        List.of(Warning.builder()
                                .type("CONFIRMED_SONGS_IN_TEAM_SCOPE")
                                .teamId(teamId)
                                .songCount((int) songSupport.countConfirmedInScope(teamId))
                                .build()));
            }
            team.reject(req.getReason().strip());
            List<Long> withdrawn = songSupport.withdrawCandidates(teamId);
            List<Long> rejected = rejectTeamOnlyApplicants(access.performanceId(), teamId);
            effects[0] = Effects.builder()
                    .withdrawnSongIds(withdrawn)
                    .rejectedPerformanceMemberIds(rejected)
                    .build();
        });
        return TeamResults.Result.builder()
                .team(r.getTeam())
                .warnings(List.of())
                .effects(effects[0])
                .build();
    }

    // 다른 팀의 승인 팀원이거나 다른 승인 근거(임원 지정·본인 신청)가 있으면 그대로 둠
    private List<Long> rejectTeamOnlyApplicants(Long performanceId, Long teamId) {
        Map<Long, Team> teams = getTeams(performanceId).stream().collect(Collectors.toMap(Team::getId, t -> t));
        List<TeamMember> all = membersOfPerformance(performanceId);
        Set<Long> inOtherApproved = all.stream()
                .filter(m -> !m.getTeam().getId().equals(teamId)
                        && teams.get(m.getTeam().getId()).getStatus() == TeamStatus.APPROVED)
                .map(m -> m.getPerformanceMember().getId())
                .collect(Collectors.toSet());
        List<Long> rejected = new ArrayList<>();
        all.stream()
                .filter(m -> m.getTeam().getId().equals(teamId))
                .map(TeamMember::getPerformanceMember)
                .filter(pm -> pm.getStatus() == PerformanceMemberStatus.APPLIED
                        && pm.getSource() == PerformanceMemberSource.TEAM_SUBMISSION
                        && !inOtherApproved.contains(pm.getId()))
                .forEach(pm -> {
                    pm.reject();
                    rejected.add(pm.getId());
                });
        return rejected;
    }

    /** API-073 철회. 승인 팀 해체는 A만. 담당 선정곡이 있거나 기본 팀이면 거절. 팀 범위 후보곡은 TEAM_WITHDRAWN. */
    @Transactional
    public TeamResults.Result withdraw(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        Effects[] effects = new Effects[1];
        TeamResults.Result r = transition(ref, teamId, req, false, (access, team) -> {
            if (team.getStatus() == TeamStatus.APPROVED) {
                access.requireAdmin();
            }
            if (team.isDefault()) {
                throw BusinessException.invalidState("기본 팀은 대체 팀을 지정한 뒤에 철회할 수 있습니다.");
            }
            if (songSupport.countAssigned(teamId) > 0) {
                throw new BusinessException(
                        ErrorCode.STATE_CONFLICT, "TEAM_ASSIGNED_TO_SONG", "담당 선정곡이 남아 있는 팀은 철회할 수 없습니다.");
            }
            team.withdraw();
            effects[0] = Effects.builder()
                    .withdrawnSongIds(songSupport.withdrawCandidates(teamId))
                    .build();
        });
        return TeamResults.Result.builder()
                .team(r.getTeam())
                .warnings(List.of())
                .effects(effects[0])
                .build();
    }

    // REJECTED·WITHDRAWN → DRAFT. 철회된 곡은 되살리지 않음
    @Transactional
    public TeamResults.Result reopen(PerformanceRef ref, Long teamId, TeamRequests.Transition req) {
        return transition(ref, teamId, req, false, (access, team) -> team.reopen());
    }

    // 069~074 공통: 공연 ACTIVE에서만, 참여 상태는 전이별로 처리
    private TeamResults.Result transition(
            PerformanceRef ref,
            Long teamId,
            TeamRequests.Transition req,
            boolean adminOnly,
            BiConsumer<PerformanceAccess, Team> action) {
        PerformanceAccess access = accessService.get(ref);
        access.requireEditable();
        access.requireStatus(PerformanceStatus.ACTIVE);
        Team team = getTeam(access.performanceId(), teamId);
        if (adminOnly) {
            access.requireAdmin();
        } else {
            requireLeader(access, team);
        }
        team.verifyVersion(req.getVersion());
        action.accept(access, team);
        return result(access, team, List.of());
    }

    // ---------- API-155 기본 팀 지정 ----------

    // F-03: 기존 해제와 새 지정을 한 트랜잭션에서 → 기본 팀은 항상 하나
    @Transactional
    public TeamResults.Result setDefault(PerformanceRef ref, TeamRequests.Default req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireAdmin();
        access.requireEditable();
        Team team = getApprovedTeam(access.performanceId(), req.getTeamId());
        team.verifyVersion(req.getVersion());
        List<Team> previous = teamRepository.findByPerformanceIdAndIsDefaultTrue(access.performanceId());
        previous.forEach(t -> t.markDefault(false));
        team.markDefault(true);
        auditLogRepository.save(AuditLog.of(
                access.clubId(),
                access.userId(),
                "DEFAULT_TEAM_CHANGED",
                "TEAM",
                team.getId(),
                req.getReason(),
                "{\"from\":"
                        + previous.stream().map(t -> String.valueOf(t.getId())).toList() + ",\"to\":" + team.getId()
                        + "}"));
        return result(access, team, List.of());
    }

    // ---------- API-086 곡 연주자로 팀 만들기 (SongService에서 호출) ----------

    /**
     * 연주자 중복을 제거해 FROM_SESSIONS·APPROVED 팀 생성. 대표 파트 = 그 사람이 맡은 파트 중 partId가 가장 작은 것.
     * 연주자 전원 APPROVED, 파트는 활성(M-02). 팀장은 연주자 중 한 명(T-09)
     */
    @Transactional
    public Team createFromSessions(
            PerformanceAccess access,
            Song song,
            List<SongSessionMember> sessions,
            String name,
            Long leaderMembershipId) {
        if (sessions.isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "SESSIONS_REQUIRED", "곡에 연주자가 없습니다.");
        }
        if (sessions.stream().anyMatch(s -> s.getPerformanceMember().getStatus() != PerformanceMemberStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "SESSIONS_NOT_APPROVED", "승인된 참여자만 팀원이 될 수 있습니다.");
        }
        if (sessions.stream().anyMatch(s -> !s.getPart().isActive())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "PART_INACTIVE", "사용 중지된 파트가 있습니다.");
        }
        Long performanceId = access.performanceId();
        String teamName = (name == null || name.isBlank())
                ? nextDefaultName(teamRepository.findNamesByPerformanceId(performanceId))
                : name.strip();
        requireUniqueName(performanceId, teamName, null);

        Map<PerformanceMember, Part> firstPart = new LinkedHashMap<>();
        sessions.stream()
                .sorted(Comparator.comparing(s -> s.getPart().getId()))
                .forEach(s -> firstPart.putIfAbsent(s.getPerformanceMember(), s.getPart()));
        PerformanceMember leader = null;
        if (leaderMembershipId != null) {
            leader = firstPart.keySet().stream()
                    .filter(pm -> pm.getMembership().getId().equals(leaderMembershipId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RULE_VIOLATION, "LEADER_NOT_TEAM_MEMBER", "팀장은 곡의 연주자 중에서 지정해야 합니다."));
        }

        Team team = teamRepository.saveAndFlush(Team.createApproved(
                access.getPerformance(),
                teamName,
                TeamSource.FROM_SESSIONS,
                access.getMembership().getUser()));
        teamMemberRepository.saveAllAndFlush(firstPart.entrySet().stream()
                .map(e -> TeamMember.of(team, e.getKey(), e.getValue()))
                .toList());
        if (leader != null) {
            team.changeLeader(leader);
        }
        song.assignTeam(team);
        return team;
    }

    // ---------- 공통 검사 ----------

    private TeamResults.Result result(PerformanceAccess access, Team team, List<Warning> warnings) {
        return TeamResults.Result.builder()
                .team(respond(access, team))
                .warnings(warnings)
                .build();
    }

    // A는 편집 가능한 공연(LOCKED는 사유 필수), L은 ACTIVE 공연의 DRAFT 팀(3.2)
    private void requireMemberEditable(PerformanceAccess access, Team team, String reason) {
        if (team.getStatus() == TeamStatus.REJECTED || team.getStatus() == TeamStatus.WITHDRAWN) {
            throw BusinessException.invalidState("반려되었거나 철회된 팀입니다.");
        }
        if (access.isAdmin()) {
            access.requireReasonIfLocked(reason);
            return;
        }
        requireLeader(access, team);
        access.requireStatus(PerformanceStatus.ACTIVE);
        requireTeamStatus(team, TeamStatus.DRAFT);
    }

    private static void requireLeader(PerformanceAccess access, Team team) {
        if (!access.isAdmin() && !team.isLeader(access.myMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "팀장만 할 수 있습니다.");
        }
    }

    private static void requireTeamStatus(Team team, TeamStatus status) {
        if (team.getStatus() != status) {
            throw BusinessException.invalidState("현재 팀 상태(" + team.getStatus() + ")에서는 할 수 없습니다.");
        }
    }

    private static void requireEligible(PerformanceAccess access, Membership m) {
        boolean ok = access.isAdmin()
                ? ELIGIBLE.contains(m.getStatus())
                : m.getMemberType() == MemberType.REGULAR && SELF_ELIGIBLE.contains(m.getStatus());
        if (!ok) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "MEMBER_NOT_ELIGIBLE", "팀원으로 지정할 수 없는 부원입니다.");
        }
    }

    /**
     * 팀원으로 넣을 참여자 확보(T-03, R-1). 미참여자는 새로 만들고, 반려·철회한 참여자는 자동 복구하지 않음.
     * approve = 임원 편성(APPROVED, ADMIN), 아니면 팀 신청 희망자(APPLIED, TEAM_SUBMISSION)
     */
    private PerformanceMember enroll(PerformanceAccess access, Membership membership, boolean approve) {
        PerformanceMember pm = accessService
                .findMemberByMembership(access.performanceId(), membership.getId())
                .orElse(null);
        if (pm == null) {
            return accessService.addMember(
                    access.getPerformance(),
                    membership,
                    approve ? PerformanceMemberStatus.APPROVED : PerformanceMemberStatus.APPLIED,
                    approve ? PerformanceMemberSource.ADMIN : PerformanceMemberSource.TEAM_SUBMISSION);
        }
        if (!pm.isActiveParticipant()) {
            throw new BusinessException(
                    ErrorCode.STATE_CONFLICT, "PARTICIPANT_NOT_RESTORABLE", "반려되었거나 철회한 참여자는 다시 넣을 수 없습니다.");
        }
        if (approve) {
            pm.approve();
        }
        return pm;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "NAME_REQUIRED", "팀 이름을 입력해 주세요.");
        }
        return name.strip();
    }

    // T-07: 공연의 모든 상태 팀 이름과 중복 금지(uk_team_performance_name, 대소문자 구분 없음)
    private void requireUniqueName(Long performanceId, String name, Team self) {
        if (self != null && self.getName().equalsIgnoreCase(name)) {
            return;
        }
        if (teamRepository.existsByPerformanceIdAndName(performanceId, name)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "DUPLICATE_TEAM_NAME", "같은 이름의 팀이 이미 있습니다.");
        }
    }

    private TeamMember getTeamMember(Team team, Long teamMemberId) {
        TeamMember member = teamMemberRepository.findById(teamMemberId).orElse(null);
        if (member == null || !member.getTeam().getId().equals(team.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "팀원을 찾을 수 없습니다.");
        }
        return member;
    }

    // 팀원 행 변경도 팀 version을 올림(API-066)
    private void forceIncrement(Team team) {
        em.lock(team, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    // T-11: 대표 파트가 본인 등록 파트에 없으면 경고(거절하지 않음)
    private List<Warning> partWarnings(List<TeamMember> members) {
        Map<Long, Set<Long>> registered = accessService.registeredPartIds(members.stream()
                .map(m -> m.getPerformanceMember().getMembership().getId())
                .collect(Collectors.toCollection(HashSet::new)));
        return members.stream()
                .filter(m -> m.getPart() != null)
                .filter(m -> !registered
                        .getOrDefault(m.getPerformanceMember().getMembership().getId(), Set.of())
                        .contains(m.getPart().getId()))
                .map(m -> Warning.builder()
                        .type("PART_NOT_REGISTERED")
                        .membershipId(m.getPerformanceMember().getMembership().getId())
                        .partId(m.getPart().getId())
                        .partName(m.getPart().getName())
                        .build())
                .toList();
    }

    private void auditIfLocked(PerformanceAccess access, Team team, String reason, String detail) {
        if (access.isLocked()) {
            auditLogRepository.save(AuditLog.of(
                    access.clubId(), access.userId(), "LOCKED_EXCEPTION_EDIT", "TEAM", team.getId(), reason, detail));
        }
    }
}
