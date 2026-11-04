package com.crane.bandwebbackend.domain.club.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 126 응답
@Getter
@RequiredArgsConstructor
public class NameAvailabilityResponse {

    private final boolean available;
}
