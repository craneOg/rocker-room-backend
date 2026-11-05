package com.crane.bandwebbackend.global.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 업로드 용도. 임시 키의 접두사가 용도를 나타낸다(U-12). 키는 올린 본인·같은 용도로만 연결할 수 있다
@Getter
@RequiredArgsConstructor
public enum ImagePurpose {
    USER_PROFILE("tmp/users/"),
    CLUB_PROFILE("tmp/club-profile/");

    private final String tmpPrefix;

    public String tmpPrefixFor(long userId) {
        return tmpPrefix + userId + "/";
    }
}
