package com.crane.bandwebbackend.domain.auth.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 카카오에서 받은 값 중 우리가 쓰는 것만. nickname·imageUrl은 비어 있을 수 있다.
// 닉네임은 개인정보라 toString을 만들지 않는다
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class KakaoProfile {

    private final String id;
    private final String nickname;
    private final String imageUrl;
}
