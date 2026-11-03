package com.crane.bandwebbackend.domain.setlist.controller;

import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import com.crane.bandwebbackend.domain.setlist.dto.SetlistItemResponse;
import com.crane.bandwebbackend.domain.setlist.dto.SetlistRequest;
import com.crane.bandwebbackend.domain.setlist.service.SetlistService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/setlist")
public class SetlistController {

    private final SetlistService setlistService;

    // API-087
    @GetMapping
    public List<SetlistItemResponse> get(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId) {
        return setlistService.get(new PerformanceRef(clubId, performanceId, userId));
    }

    // API-088
    @PutMapping("/order")
    public List<SetlistItemResponse> save(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @Valid @RequestBody SetlistRequest req) {
        return setlistService.save(new PerformanceRef(clubId, performanceId, userId), req);
    }
}
