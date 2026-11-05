package com.crane.bandwebbackend.domain.club.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 13 요청. 형식 검사는 서비스가 한다(형식 오류도 없는 코드와 같은 404로 답해야 해서 Bean Validation을 쓰지 않는다)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JoinCodeResolveRequest {

    private String joinCode;
}
