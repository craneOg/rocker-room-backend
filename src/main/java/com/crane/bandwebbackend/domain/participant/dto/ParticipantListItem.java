package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import java.time.OffsetDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipantListItem(String id, String performanceId, String membershipId,
								  PerformanceMemberStatus status, PerformanceMemberSource source,
								  OffsetDateTime createdAt, OffsetDateTime updatedAt,
								  String displayName, String profileImageUrl, MemberType memberType,
								  MembershipStatus membershipStatus, List<Part> parts,
								  List<String> teamNames, long playedSongCount) {
	public record Part(String id, String name) {
	}
}
