package com.crane.bandwebbackend.domain.club.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 11 요청. 값 검사는 서비스가 한다(오류 details에 field를 담기 위해 clubType도 문자열로 받는다)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClubCreateRequest {

    private String name;
    private String description;
    private String clubType;

    // Lombok getter는 isSearchable()이라 JSON 이름을 필드에 직접 지정한다
    @JsonProperty("isSearchable")
    private boolean searchable;

    // API 129(purpose=CLUB_PROFILE)로 받은 임시 키
    private String profileImageKey;

    private String themeColor;
    private boolean issueJoinCode;
}
