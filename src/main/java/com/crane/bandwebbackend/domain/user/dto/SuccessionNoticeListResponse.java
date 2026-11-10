package com.crane.bandwebbackend.domain.user.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 135 응답 data: 페이지 없이 items만
@Getter
@RequiredArgsConstructor
public class SuccessionNoticeListResponse {

    private final List<SuccessionNoticeResponse> items;
}
