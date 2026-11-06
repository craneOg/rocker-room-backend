package com.crane.bandwebbackend.domain.user.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 128 응답 data
@Getter
@RequiredArgsConstructor
public class HandleAvailabilityResponse {

    private final boolean available;
}
