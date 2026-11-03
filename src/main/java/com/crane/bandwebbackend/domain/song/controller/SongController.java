package com.crane.bandwebbackend.domain.song.controller;

import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.song.dto.SongRequests;
import com.crane.bandwebbackend.domain.song.dto.SongViews;
import com.crane.bandwebbackend.domain.song.service.SongService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

// 인증 주체 = users.id(Long). 보안 필터가 principal로 넣어 준다는 전제
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/songs")
public class SongController {

    private final SongService songService;

    // API-075
    @GetMapping
    public SongViews.ListResult list(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long candidateTeamId,
            @RequestParam(defaultValue = "false") boolean proposedByMe,
            @RequestParam(defaultValue = "false") boolean unresponded,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return songService.list(
                ref(clubId, performanceId, userId), status, candidateTeamId, proposedByMe, unresponded, sort, page, size);
    }

    // API-076
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SongViews.Result create(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Create req) {
        return songService.create(ref(clubId, performanceId, userId), req);
    }

    // API-077
    @GetMapping("/{songId}")
    public SongViews.Detail detail(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId) {
        return songService.detail(ref(clubId, performanceId, userId), songId);
    }

    // API-078
    @PatchMapping("/{songId}")
    public SongViews.Result update(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Update req) {
        return songService.update(ref(clubId, performanceId, userId), songId, req);
    }

    // API-079
    @PutMapping("/{songId}/requirements")
    public SongViews.Result requirements(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Requirements req) {
        return songService.replaceRequirements(ref(clubId, performanceId, userId), songId, req);
    }

    // API-080
    @PostMapping("/{songId}/confirm")
    public SongViews.Result confirm(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Confirm req) {
        return songService.confirm(ref(clubId, performanceId, userId), songId, req);
    }

    // API-081
    @PostMapping("/{songId}/unconfirm")
    public SongViews.Result unconfirm(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Decision req) {
        return songService.unconfirm(ref(clubId, performanceId, userId), songId, req);
    }

    // API-082
    @PostMapping("/{songId}/reject")
    public SongViews.Result reject(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Decision req) {
        return songService.reject(ref(clubId, performanceId, userId), songId, req);
    }

    // API-083
    @PostMapping("/{songId}/withdraw")
    public SongViews.Result withdraw(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Decision req) {
        return songService.withdraw(ref(clubId, performanceId, userId), songId, req);
    }

    // API-156
    @PostMapping("/{songId}/reconsider")
    public SongViews.Result reconsider(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Decision req) {
        return songService.reconsider(ref(clubId, performanceId, userId), songId, req);
    }

    // API-157
    @PostMapping("/{songId}/remove")
    public SongViews.Result remove(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Decision req) {
        return songService.remove(ref(clubId, performanceId, userId), songId, req);
    }

    // API-084
    @PutMapping("/{songId}/assigned-team")
    public SongViews.Result assignTeam(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.AssignTeam req) {
        return songService.assignTeam(ref(clubId, performanceId, userId), songId, req);
    }

    // API-085
    @PutMapping("/{songId}/sessions")
    public SongViews.Result sessions(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SongRequests.Sessions req) {
        return songService.replaceSessions(ref(clubId, performanceId, userId), songId, req);
    }

    private static PerformanceRef ref(Long clubId, Long performanceId, Long userId) {
        return new PerformanceRef(clubId, performanceId, userId);
    }
}
