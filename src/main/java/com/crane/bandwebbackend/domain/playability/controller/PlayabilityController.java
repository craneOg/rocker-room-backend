package com.crane.bandwebbackend.domain.playability.controller;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationRows;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.playability.dto.PlayabilityRequest;
import com.crane.bandwebbackend.domain.playability.service.PlayabilityService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/songs/{songId}/playability")
public class PlayabilityController {

    private final PlayabilityService playabilityService;

    // API-090
    @GetMapping
    public List<EvaluationRows.Playability> list(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @RequestParam(required = false) Long performanceMemberId) {
        return playabilityService.list(ref(clubId, performanceId, userId), songId, performanceMemberId);
    }

    // API-091
    @PutMapping("/me/{partId}")
    public EvaluationRows.Playability saveMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long partId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @Valid @RequestBody PlayabilityRequest req) {
        return playabilityService.saveMine(ref(clubId, performanceId, userId), songId, partId, req);
    }

    // API-093
    @DeleteMapping("/me/{partId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long partId,
            @AuthenticationPrincipal(expression = "userId()") Long userId) {
        playabilityService.deleteMine(ref(clubId, performanceId, userId), songId, partId);
    }

    // API-092
    @PutMapping("/members/{performanceMemberId}/{partId}")
    public EvaluationRows.Playability saveFor(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long performanceMemberId,
            @PathVariable Long partId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @Valid @RequestBody PlayabilityRequest req) {
        return playabilityService.saveFor(ref(clubId, performanceId, userId), songId, performanceMemberId, partId, req);
    }

    // API-094
    @DeleteMapping("/members/{performanceMemberId}/{partId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFor(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long performanceMemberId,
            @PathVariable Long partId,
            @AuthenticationPrincipal(expression = "userId()") Long userId) {
        playabilityService.deleteFor(ref(clubId, performanceId, userId), songId, performanceMemberId, partId);
    }

    private static PerformanceRef ref(Long clubId, Long performanceId, Long userId) {
        return new PerformanceRef(clubId, performanceId, userId);
    }
}
