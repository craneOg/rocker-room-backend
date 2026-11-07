package com.crane.bandwebbackend.domain.participant.dto;

import java.util.List;
import java.util.Map;

public record ParticipantListResponse(List<ParticipantListItem> items, Map<String, Long> statusCounts,
									  int page, int size, long totalElements, int totalPages) {
}
