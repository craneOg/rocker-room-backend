package com.crane.bandwebbackend.domain.participant.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** API-056: 검증 실패 시 전체 트랜잭션을 취소한다. */
public record ParticipantBulkRequest(
        @NotEmpty(message = "추가할 부원은 필수입니다.") List<String> membershipIds, String reason) {}
