package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import com.crane.bandwebbackend.domain.auth.repository.OneTimeTokenRepository;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.auth.service.AccountRestorationService;
import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.CoreEntityLookupService;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalService;
import com.crane.bandwebbackend.domain.participant.service.TeamLeaderSuccession;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse.Candidate;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse.ClubItem;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse.Succession;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse.TeamCandidate;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalPreviewResponse.TeamLeaderTransfer;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalRequest;
import com.crane.bandwebbackend.domain.user.dto.AccountWithdrawalResponse;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.entity.UserWithdrawalArchive;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.domain.user.repository.UserWithdrawalArchiveRepository;
import com.crane.bandwebbackend.domain.user.repository.WithdrawalPreviewQueryRepository;
import com.crane.bandwebbackend.domain.user.repository.WithdrawalPreviewQueryRepository.LedTeam;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// API 134·009: 서비스 탈퇴. 승계 계산은 소속 탈퇴(030)의 서비스를 재사용한다(X1).
// 잠금 순서: 사용자 행 → 동아리 행(clubId 오름차순) → 소속 → 공연 → 팀
@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private static final int MAX_ITEMS = 100;

    private final MembershipService memberships;
    private final MembershipWithdrawalService clubWithdrawals;
    private final TeamLeaderSuccession teamSuccession;
    private final CoreEntityLookupService lookup;
    private final WithdrawalPreviewQueryRepository queries;
    private final UserRepository userRepository;
    private final UserWithdrawalArchiveRepository archiveRepository;
    private final RefreshTokenRepository refreshTokens;
    private final OneTimeTokenRepository oneTimeTokens;
    private final AuditRecorder auditRecorder;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    // 요청 검사 결과
    private static class Parsed {

        private String reason;
        private boolean confirmed;
        private final Map<Long, Long> successors = new HashMap<>();
        private final Map<Long, Long> teamLeaders = new HashMap<>();
    }

    // 1단계 뒤 소속 동아리가 바뀐 것. 트랜잭션을 되돌리고 한 번 다시 시도한다
    private static class ClubSetChanged extends RuntimeException {

        ClubSetChanged() {
            super(null, null, false, false);
        }
    }

    // API 134: 데이터를 바꾸지 않는다
    public AccountWithdrawalPreviewResponse preview(long userId) {
        return readOnly().execute(status -> computePreview(userId));
    }

    public AccountWithdrawalResponse withdraw(long userId, AccountWithdrawalRequest request) {
        Parsed parsed = parse(request == null ? new AccountWithdrawalRequest() : request);
        if (!parsed.confirmed) {
            // 요청의 지정값은 반영하지 않고 134와 같은 기본 후임으로 계산한다
            AccountWithdrawalPreviewResponse preview = preview(userId);
            throw BusinessException.confirmationRequired(
                    "탈퇴 전 확인이 필요합니다.",
                    List.of(ErrorDetail.of(null, "WITHDRAWAL_CONFIRMATION_REQUIRED")),
                    preview.getWarnings());
        }
        // 소속 동아리 집합을 먼저 읽고 잠근 뒤 다시 읽어 같은지 본다. 다르면 한 번만 다시 시도한다
        for (int attempt = 0; ; attempt++) {
            Set<Long> clubIds = readOnly().execute(status -> lockedClubIds(memberships.membershipsOfUser(userId)));
            try {
                return new TransactionTemplate(transactionManager).execute(status -> execute(userId, parsed, clubIds));
            } catch (ClubSetChanged e) {
                if (attempt > 0) {
                    throw BusinessException.invalidState("탈퇴 처리 중 소속이 바뀌었습니다. 다시 시도해 주세요.");
                }
            }
        }
    }

    // ---- 134 ----

    private AccountWithdrawalPreviewResponse computePreview(long userId) {
        List<Membership> mine = memberships.accessibleMembershipsOfUser(userId).stream()
                .sorted(Comparator.comparing(member -> member.getClub().getId()))
                .toList();
        Map<Long, List<LedTeam>> teamsByClub = new HashMap<>();
        queries.findLedTeams(userId)
                .forEach(team -> teamsByClub
                        .computeIfAbsent(team.getClubId(), key -> new ArrayList<>())
                        .add(team));
        List<ClubItem> clubs = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Membership member : mine) {
            Club club = member.getClub();
            Long clubId = club.getId();
            Succession succession = notRequired();
            // 비활성 동아리는 임원 승계를 하지 않는다
            if (club.getStatus() == ClubStatus.ACTIVE
                    && member.getRole() == MembershipRole.CLUB_ADMIN
                    && clubWithdrawals.requiresAdminSuccession(clubId, member.getId())) {
                List<Candidate> candidates = clubWithdrawals.adminSuccessorCandidates(clubId, member.getId()).stream()
                        .map(candidate -> new Candidate(
                                candidate.getId(),
                                candidate.getUser().getId(),
                                candidate.getDisplayName() != null
                                        ? candidate.getDisplayName()
                                        : candidate.getUser().getName()))
                        .toList();
                if (candidates.isEmpty()) {
                    succession = new Succession("NONE", null, null, null, List.of());
                    warnings.add(warning("CLUB_DEACTIVATED", club, null, null));
                } else {
                    Candidate first = candidates.get(0);
                    succession = new Succession(
                            "DESIGNATED_OR_EARLIEST",
                            first.getMembershipId(),
                            first.getUserId(),
                            first.getDisplayName(),
                            candidates);
                    warnings.add(warning("ADMIN_SUCCESSION", club, null, first));
                }
            }
            List<TeamLeaderTransfer> transfers = new ArrayList<>();
            for (LedTeam team : teamsByClub.getOrDefault(clubId, List.of())) {
                transfers.add(teamTransfer(club, team, warnings));
            }
            clubs.add(new ClubItem(clubId, club.getName(), member.getRole().name(), succession, transfers));
        }
        return new AccountWithdrawalPreviewResponse(clubs, warnings);
    }

    private static Succession notRequired() {
        return new Succession("NOT_REQUIRED", null, null, null, List.of());
    }

    private TeamLeaderTransfer teamTransfer(Club club, LedTeam team, List<Map<String, Object>> warnings) {
        if ("DRAFT".equals(team.getTeamStatus())) {
            warnings.add(warning("TEAM_DELETED", club, team, null));
            return transfer(team, null, List.of());
        }
        List<Object[]> rows = teamSuccession.candidatesOf(team.getTeamId(), team.getLeaderMemberId());
        Map<Long, String> names = queries.displayNames(
                rows.stream().map(row -> ((Number) row[1]).longValue()).toList());
        List<Candidate> candidates = rows.stream()
                .map(row -> {
                    long membershipId = ((Number) row[1]).longValue();
                    return new Candidate(membershipId, ((Number) row[2]).longValue(), names.get(membershipId));
                })
                .toList();
        Candidate first = candidates.isEmpty() ? null : candidates.get(0);
        warnings.add(warning(
                "SUBMITTED".equals(team.getTeamStatus()) ? "TEAM_AUTO_SUCCESSION" : "TEAM_LEADER_TRANSFER",
                club,
                team,
                first));
        return transfer(team, first, candidates);
    }

    private static TeamLeaderTransfer transfer(LedTeam team, Candidate first, List<Candidate> candidates) {
        return new TeamLeaderTransfer(
                team.getPerformanceId(),
                team.getPerformanceName(),
                team.getTeamId(),
                team.getTeamName(),
                team.getTeamStatus(),
                first == null ? null : first.getMembershipId(),
                candidates.stream()
                        .map(candidate -> new TeamCandidate(candidate.getMembershipId(), candidate.getDisplayName()))
                        .toList());
    }

    // 해당 없는 키도 null로 내려야 해서 맵으로 만든다. 맵 안에는 @IdString이 적용되지 않아 ID는 문자열로 넣는다
    private static Map<String, Object> warning(String type, Club club, LedTeam team, Candidate successor) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("type", type);
        warning.put("clubId", String.valueOf(club.getId()));
        warning.put("clubName", club.getName());
        warning.put("performanceId", team == null ? null : String.valueOf(team.getPerformanceId()));
        warning.put("performanceName", team == null ? null : team.getPerformanceName());
        warning.put("teamId", team == null ? null : String.valueOf(team.getTeamId()));
        warning.put("teamName", team == null ? null : team.getTeamName());
        Map<String, Object> successorMap = null;
        if (successor != null) {
            successorMap = new LinkedHashMap<>();
            successorMap.put("membershipId", String.valueOf(successor.getMembershipId()));
            successorMap.put("userId", String.valueOf(successor.getUserId()));
            successorMap.put("displayName", successor.getDisplayName());
        }
        warning.put("successor", successorMap);
        return warning;
    }

    // ---- 009 ----

    private Parsed parse(AccountWithdrawalRequest request) {
        if (request.isSuccessorsNull()) {
            throw invalid("successors");
        }
        if (request.isTeamLeaderSuccessorsNull()) {
            throw invalid("teamLeaderSuccessors");
        }
        if (request.isConfirmedNull()) {
            throw invalid("confirmed");
        }
        Parsed parsed = new Parsed();
        parsed.confirmed = Boolean.TRUE.equals(request.getConfirmed());
        if (request.getReason() != null) {
            String reason = request.getReason().strip();
            if (reason.isEmpty() || reason.length() > 500) {
                throw invalid("reason");
            }
            parsed.reason = reason;
        }
        if (request.getSuccessors().size() > MAX_ITEMS) {
            throw invalid("successors");
        }
        for (var item : request.getSuccessors()) {
            if (item == null) {
                throw invalid("successors");
            }
            Long clubId = ApiIds.positive(item.getClubId(), "successors.clubId");
            Long userId = ApiIds.positive(item.getUserId(), "successors.userId");
            if (parsed.successors.put(clubId, userId) != null) {
                throw invalid("successors");
            }
        }
        if (request.getTeamLeaderSuccessors().size() > MAX_ITEMS) {
            throw invalid("teamLeaderSuccessors");
        }
        for (var item : request.getTeamLeaderSuccessors()) {
            if (item == null) {
                throw invalid("teamLeaderSuccessors");
            }
            Long teamId = ApiIds.positive(item.getTeamId(), "teamLeaderSuccessors.teamId");
            Long membershipId = ApiIds.positive(item.getMembershipId(), "teamLeaderSuccessors.membershipId");
            if (parsed.teamLeaders.put(teamId, membershipId) != null) {
                throw invalid("teamLeaderSuccessors");
            }
        }
        return parsed;
    }

    // 잠글 동아리: 유효 소속과 PENDING·INVITED 소속의 동아리. PENDING·INVITED도 이 트랜잭션에서 바꾸므로
    // 승인·초대 수락(decide, 동아리 잠금)과 직렬화해야 한다(§0.4)
    private Set<Long> lockedClubIds(List<Membership> mine) {
        Set<Long> clubIds = new TreeSet<>();
        mine.stream()
                .filter(member -> isValid(member.getStatus())
                        || member.getStatus() == MembershipStatus.PENDING
                        || member.getStatus() == MembershipStatus.INVITED)
                .forEach(member -> clubIds.add(member.getClub().getId()));
        return clubIds;
    }

    private AccountWithdrawalResponse execute(long userId, Parsed parsed, Set<Long> clubIds) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AccountWithdrawalService::unauthenticated);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw unauthenticated();
        }
        // 동아리 행을 먼저 잠근 뒤에야 처음으로 일반 읽기를 해서, 잠금 전 스냅숏이 낡은 값을 보이지 않게 한다
        clubIds.forEach(lookup::lockClub);
        List<Membership> mine = memberships.membershipsOfUser(userId);
        if (!lockedClubIds(mine).equals(clubIds)) {
            throw new ClubSetChanged();
        }
        // 승계·소속 탈퇴는 유효 소속의 동아리만 처리한다
        Set<Long> validClubIds = new TreeSet<>();
        mine.stream()
                .filter(member -> isValid(member.getStatus()))
                .forEach(member -> validClubIds.add(member.getClub().getId()));
        // 팀장 지정값을 동아리별로 나눈다. 본인이 팀장이 아닌 팀은 422
        Map<Long, Long> teamClubs = queries.teamClubIds(userId);
        Map<Long, Map<Long, Long>> teamLeadersByClub = new HashMap<>();
        parsed.teamLeaders.forEach((teamId, membershipId) -> {
            Long clubId = teamClubs.get(teamId);
            if (clubId == null) {
                throw notEligible("팀장 후임의 팀이 대상과 연결되지 않았습니다.");
            }
            teamLeadersByClub.computeIfAbsent(clubId, key -> new HashMap<>()).put(teamId, membershipId);
        });
        Set<Long> unusedSuccessors = new HashSet<>(parsed.successors.keySet());
        Map<Long, Long> clubSuccessions = new TreeMap<>();
        for (Long clubId : validClubIds) {
            unusedSuccessors.remove(clubId);
            Map<Long, Long> teamLeaders = teamLeadersByClub.remove(clubId);
            Long successorUserId = clubWithdrawals.withdrawForAccount(
                    clubId, userId, parsed.successors.get(clubId), teamLeaders == null ? new HashMap<>() : teamLeaders);
            if (successorUserId != null) {
                clubSuccessions.put(clubId, successorUserId);
            }
        }
        // 소속이 없는 동아리·처리하지 않은 동아리의 지정값은 자격 없음
        if (!unusedSuccessors.isEmpty() || !teamLeadersByClub.isEmpty()) {
            throw notEligible("후임을 지정할 수 없는 동아리 또는 팀입니다.");
        }
        // DB DATETIME(6)은 마이크로초까지 보관(더 미세한 자릿수는 MySQL이 반올림)하므로 잘라서 응답을 보관값·API-131과 일치시킨다
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        for (Membership member : mine) {
            if (member.getStatus() == MembershipStatus.PENDING) {
                member.transitionTo(MembershipStatus.WITHDRAWN, now);
            } else if (member.getStatus() == MembershipStatus.INVITED) {
                member.transitionTo(MembershipStatus.DECLINED, now);
            }
            member.changeDisplayName(null);
        }
        archiveRepository.save(UserWithdrawalArchive.of(user, now));
        user.withdraw(now);
        refreshTokens.revokeAllByUser(userId, now, RevokeReason.WITHDRAWN);
        oneTimeTokens.deleteUnusedByUser(userId);
        auditRecorder.record(
                null,
                userId,
                "ACCOUNT_WITHDRAWN",
                "USER",
                userId,
                parsed.reason,
                Map.of("before", Map.of("status", "ACTIVE"), "after", Map.of("status", "WITHDRAWN")));
        userRepository.flush();
        // U-07 카카오 연결 끊기: 보류(Q3). 여기서 호출하고 실패하면 예외로 전체 롤백
        Map<Long, Long> newLeaders = new TreeMap<>(queries.newLeaders(teamClubs.keySet(), userId));
        return new AccountWithdrawalResponse(
                KstTime.toKst(now),
                KstTime.toKst(now.plus(AccountRestorationService.RESTORABLE_PERIOD)),
                new AccountWithdrawalResponse.Successions(
                        clubSuccessions.entrySet().stream()
                                .map(entry ->
                                        new AccountWithdrawalResponse.ClubSuccession(entry.getKey(), entry.getValue()))
                                .toList(),
                        newLeaders.entrySet().stream()
                                .map(entry ->
                                        new AccountWithdrawalResponse.TeamSuccession(entry.getKey(), entry.getValue()))
                                .toList()));
    }

    private static boolean isValid(MembershipStatus status) {
        return status == MembershipStatus.ACTIVE
                || status == MembershipStatus.ON_LEAVE
                || status == MembershipStatus.GRADUATED;
    }

    private TransactionTemplate readOnly() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
    }

    private static BusinessException notEligible(String message) {
        return new BusinessException(ErrorCode.RULE_VIOLATION, "SUCCESSOR_NOT_ELIGIBLE", message);
    }

    private static BusinessException invalid(String field) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of(field, "INVALID_VALUE")));
    }
}
