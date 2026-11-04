package com.crane.bandwebbackend.domain.membership.controller;

import com.crane.bandwebbackend.domain.membership.dto.MembershipBulkRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipBulkResponse;
import com.crane.bandwebbackend.domain.membership.service.MembershipBulkService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/memberships/bulk")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MembershipBulkController {
	private final MembershipBulkService service;
	private final CurrentActor currentActor;

	/** 소속 일괄 요청을 처리한다 */
	@PostMapping
	public ResponseEntity<ApiResponse<MembershipBulkResponse>> process(
		@PathVariable String clubId,
		@Valid @RequestBody MembershipBulkRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.of(service.process(ApiIds.positive(clubId, "clubId"),
				currentActor.requireUserId(), body), RequestIdFilter.require(request)));
	}
}
