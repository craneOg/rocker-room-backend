package com.crane.bandwebbackend.domain.vote.controller;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationRows;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.vote.dto.VoteRequest;
import com.crane.bandwebbackend.domain.vote.service.VoteService;
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
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/songs/{songId}/votes")
public class VoteController {

    private final VoteService voteService;

    // API-095
    @GetMapping
    public List<EvaluationRows.Vote> list(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long performanceMemberId) {
        return voteService.list(new PerformanceRef(clubId, performanceId, userId), songId, performanceMemberId);
    }

    // API-096
    @PutMapping("/me")
    public EvaluationRows.Vote voteMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody VoteRequest req) {
        return voteService.voteMine(new PerformanceRef(clubId, performanceId, userId), songId, req);
    }

    // API-098
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal Long userId) {
        voteService.deleteMine(new PerformanceRef(clubId, performanceId, userId), songId);
    }

    // API-097
    @PutMapping("/members/{performanceMemberId}")
    public EvaluationRows.Vote voteFor(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long performanceMemberId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody VoteRequest req) {
        return voteService.voteFor(new PerformanceRef(clubId, performanceId, userId), songId, performanceMemberId, req);
    }

    // API-099
    @DeleteMapping("/members/{performanceMemberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFor(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @PathVariable Long performanceMemberId,
            @AuthenticationPrincipal Long userId) {
        voteService.deleteFor(new PerformanceRef(clubId, performanceId, userId), songId, performanceMemberId);
    }
}
