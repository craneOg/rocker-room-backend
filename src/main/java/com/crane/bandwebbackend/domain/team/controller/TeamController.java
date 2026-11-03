package com.crane.bandwebbackend.domain.team.controller;

import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.song.service.SongService;
import com.crane.bandwebbackend.domain.team.dto.TeamDetailResponse;
import com.crane.bandwebbackend.domain.team.dto.TeamRequests;
import com.crane.bandwebbackend.domain.team.dto.TeamResults;
import com.crane.bandwebbackend.domain.team.service.TeamQueryService;
import com.crane.bandwebbackend.domain.team.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamQueryService teamQueryService;
    private final SongService songService;

    // API-061
    @GetMapping
    public TeamResults.ListResult list(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine) {
        return teamQueryService.list(ref(clubId, performanceId, userId), status, mine);
    }

    // API-062 / API-086(C7: 본문에 sourceSongId가 있으면 곡 연주자로 팀 만들기)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResults.Result create(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Create req) {
        PerformanceRef ref = ref(clubId, performanceId, userId);
        return req.getSourceSongId() != null ? songService.createTeamFromSong(ref, req) : teamService.create(ref, req);
    }

    // API-154
    @GetMapping("/default-name")
    public TeamResults.DefaultName defaultName(
            @PathVariable Long clubId, @PathVariable Long performanceId, @AuthenticationPrincipal Long userId) {
        return teamService.defaultName(ref(clubId, performanceId, userId));
    }

    // API-155
    @PutMapping("/default")
    public TeamResults.Result setDefault(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Default req) {
        return teamService.setDefault(ref(clubId, performanceId, userId), req);
    }

    // API-063
    @GetMapping("/{teamId}")
    public TeamDetailResponse detail(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return teamQueryService.detail(ref(clubId, performanceId, userId), teamId);
    }

    // API-064
    @PatchMapping("/{teamId}")
    public TeamResults.Result rename(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Rename req) {
        return teamService.rename(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-065
    @PostMapping("/{teamId}/members")
    public TeamResults.Result addMember(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.AddMember req) {
        return teamService.addMember(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-066
    @PatchMapping("/{teamId}/members/{teamMemberId}")
    public TeamResults.Result changeMemberPart(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @PathVariable Long teamMemberId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.ChangePart req) {
        return teamService.changeMemberPart(ref(clubId, performanceId, userId), teamId, teamMemberId, req);
    }

    // API-067. version·removeSessions·reason은 본문으로
    @DeleteMapping("/{teamId}/members/{teamMemberId}")
    public TeamResults.Result removeMember(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @PathVariable Long teamMemberId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.RemoveMember req) {
        return teamService.removeMember(ref(clubId, performanceId, userId), teamId, teamMemberId, req);
    }

    // API-068
    @PutMapping("/{teamId}/leader")
    public TeamResults.Result changeLeader(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Leader req) {
        return teamService.changeLeader(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-069
    @PostMapping("/{teamId}/submit")
    public TeamResults.Result submit(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.submit(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-070
    @PostMapping("/{teamId}/unsubmit")
    public TeamResults.Result unsubmit(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.unsubmit(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-071
    @PostMapping("/{teamId}/approve")
    public TeamResults.Result approve(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.approve(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-072
    @PostMapping("/{teamId}/reject")
    public TeamResults.Result reject(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.reject(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-073
    @PostMapping("/{teamId}/withdraw")
    public TeamResults.Result withdraw(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.withdraw(ref(clubId, performanceId, userId), teamId, req);
    }

    // API-074
    @PostMapping("/{teamId}/reopen")
    public TeamResults.Result reopen(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamRequests.Transition req) {
        return teamService.reopen(ref(clubId, performanceId, userId), teamId, req);
    }

    private static PerformanceRef ref(Long clubId, Long performanceId, Long userId) {
        return new PerformanceRef(clubId, performanceId, userId);
    }
}
