package com.crane.bandwebbackend.domain.audition.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record MyAuditionsResponse(
		List<MyAuditionItem> items
) {
}
