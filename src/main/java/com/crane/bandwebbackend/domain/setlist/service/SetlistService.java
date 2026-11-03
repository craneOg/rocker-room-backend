package com.crane.bandwebbackend.domain.setlist.service;

import com.crane.bandwebbackend.domain.participant.service.PerformanceAccess;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.setlist.dto.SetlistItemResponse;
import com.crane.bandwebbackend.domain.setlist.dto.SetlistRequest;
import com.crane.bandwebbackend.domain.song.dto.SongViews;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import com.crane.bandwebbackend.domain.song.service.SongService;
import com.crane.bandwebbackend.domain.song.service.SongSupport;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SetlistService {

    // 배정 순서대로, 미배정은 끝에 id순
    private static final Comparator<Song> ORDER = Comparator.comparing(
                    Song::getSetlistOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Song::getId);

    private final PerformanceAccessService accessService;
    private final SongService songService;
    private final SongSupport songSupport;
    private final TeamService teamService;

    /**
     * API-087. CONFIRMED 곡만. GUEST는 LOCKED·FINISHED 공연에서 전체 곡의 title·artist·order만,
     * 그 전에는 본인 곡만. sessions는 본인 곡에 대해서만
     */
    public List<SetlistItemResponse> get(PerformanceRef ref) {
        PerformanceAccess access = accessService.get(ref);
        Long pid = access.performanceId();
        Map<Long, List<SongSessionMember>> sessions = songSupport.sessionsOfPerformance(pid).stream()
                .collect(Collectors.groupingBy(s -> s.getSong().getId()));
        Map<Long, Set<Long>> teamMembers = teamService.memberIdsByTeam(pid);
        boolean guestSeesAll = access.status() == PerformanceStatus.LOCKED || access.status() == PerformanceStatus.FINISHED;
        return songService.getConfirmedSongs(pid).stream()
                .sorted(ORDER)
                .map(song -> {
                    List<SongSessionMember> rows = sessions.getOrDefault(song.getId(), List.of());
                    boolean related = !access.isGuest()
                            || SongSupport.canSee(
                                    access,
                                    song,
                                    teamMembers,
                                    rows.stream().map(r -> r.getPerformanceMember().getId()).collect(Collectors.toSet()));
                    if (!related && !guestSeesAll) {
                        return null;
                    }
                    return item(song, related ? rows : List.of(), related, teamMembers);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // API-088. 곡 version 전부와 공연 version을 한 트랜잭션에서 검사(O-01·O-03)
    @Transactional
    public List<SetlistItemResponse> save(PerformanceRef ref, SetlistRequest req) {
        PerformanceAccess access = accessService.getForUpdate(ref);
        access.requireAdmin();
        access.requireEditable();
        if (!Objects.equals(access.getPerformance().getVersion(), req.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        Map<Long, Song> confirmed = songService.getConfirmedSongs(access.performanceId()).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        // 낡은 화면에서 저장해 곡이 빠지거나 끼는 일을 막기 위해 확정 곡 전체를 받음
        Map<Song, Integer> orders = new HashMap<>();
        for (SetlistRequest.Item item : req.getItems()) {
            Song song = confirmed.get(item.getSongId());
            if (song == null || orders.containsKey(song)) {
                throw new BusinessException(
                        ErrorCode.VERSION_CONFLICT, "SETLIST_CHANGED", "선정곡 목록이 바뀌었습니다. 새로고침 후 다시 저장해 주세요.");
            }
            song.verifyVersion(item.getVersion());
            orders.put(song, item.getOrder());
        }
        if (orders.size() != confirmed.size()) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT, "SETLIST_CHANGED", "선정곡 목록이 바뀌었습니다. 새로고침 후 다시 저장해 주세요.");
        }
        List<Integer> assigned = orders.values().stream().filter(Objects::nonNull).sorted().toList();
        for (int i = 0; i < assigned.size(); i++) {
            if (assigned.get(i) != i + 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_ORDER", "순서는 1부터 빠짐없이 중복 없이 지정해야 합니다.");
            }
        }
        // LOCKED는 전곡 배정 상태를 유지(L-01)
        if (access.isLocked() && assigned.size() != orders.size()) {
            throw new BusinessException(
                    ErrorCode.RULE_VIOLATION, "LOCK_REQUIREMENTS_FAILED", "확정된 공연에서는 모든 곡의 순서를 정해야 합니다.");
        }
        songService.applySetlistOrders(orders);
        return get(ref);
    }

    private static SetlistItemResponse item(
            Song song, List<SongSessionMember> rows, boolean full, Map<Long, Set<Long>> teamMembers) {
        Set<Long> team = song.getAssignedTeam() == null
                ? Set.of()
                : teamMembers.getOrDefault(song.getAssignedTeam().getId(), Set.of());
        return SetlistItemResponse.builder()
                .song(SetlistItemResponse.Song.builder()
                        .id(song.getId())
                        .title(song.getTitle())
                        .artist(song.getArtist())
                        .setlistOrder(song.getSetlistOrder())
                        .assignedTeamId(full && song.getAssignedTeam() != null ? song.getAssignedTeam().getId() : null)
                        .assignedTeamName(
                                full && song.getAssignedTeam() != null ? song.getAssignedTeam().getName() : null)
                        .version(full ? song.getVersion() : null)
                        .build())
                .sessions(rows.stream()
                        .map(s -> SongViews.Session.builder()
                                .id(s.getId())
                                .performanceMemberId(s.getPerformanceMember().getId())
                                .membershipId(s.getPerformanceMember().getMembership().getId())
                                .displayName(PerformanceAccessService.displayName(s.getPerformanceMember()))
                                .partId(s.getPart().getId())
                                .partName(s.getPart().getName())
                                .isSupport(song.getAssignedTeam() != null
                                        && !team.contains(s.getPerformanceMember().getId()))
                                .isGuest(s.getPerformanceMember().getMembership().getMemberType() == MemberType.GUEST)
                                .build())
                        .toList())
                .build();
    }
}
