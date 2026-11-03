package com.crane.bandwebbackend.domain.evaluation.controller;

import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationMatrixResponse;
import com.crane.bandwebbackend.domain.evaluation.dto.EvaluationType;
import com.crane.bandwebbackend.domain.evaluation.dto.SongEvaluationResponse;
import com.crane.bandwebbackend.domain.evaluation.service.EvaluationService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}")
public class EvaluationController {

    private final EvaluationService evaluationService;

    // API-089
    @GetMapping("/songs/{songId}/evaluations")
    public SongEvaluationResponse song(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long songId,
            @AuthenticationPrincipal(expression = "userId()") Long userId) {
        return evaluationService.songEvaluation(new PerformanceRef(clubId, performanceId, userId), songId);
    }

    // API-158
    @GetMapping("/evaluations")
    public EvaluationMatrixResponse matrix(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @RequestParam(required = false) EvaluationType type,
            @RequestParam(required = false) Long teamId) {
        return evaluationService.matrix(new PerformanceRef(clubId, performanceId, userId), type, teamId);
    }
}
