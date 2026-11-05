package com.crane.bandwebbackend.domain.club.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 127 요청. 형식 검사는 서비스가 한다(형식 오류도 같은 404)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvitationPreviewRequest {

    private String code;
}
