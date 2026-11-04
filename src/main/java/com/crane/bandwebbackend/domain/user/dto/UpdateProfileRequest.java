package com.crane.bandwebbackend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 8 요청. 보내지 않은 필드는 바꾸지 않는다. profileImageUrl만 null이 허용된 값("사진 삭제")이라
// 보내지 않은 것과 null을 보낸 것을 구분한다. 나머지 필드의 null은 보내지 않은 것으로 본다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateProfileRequest {

    @JsonProperty("name")
    @Size(min = 1, max = 50)
    private String name;

    @JsonProperty("handle")
    @Pattern(regexp = "^[a-z0-9_]{5,30}$")
    private String handle;

    // API 129로 받은 임시 키. 요청 필드명은 명세대로 profileImageUrl이다
    private String profileImageUrl;

    private boolean profileImageUrlSpecified;

    @JsonProperty("profileImagePublic")
    private Boolean profileImagePublic;

    @JsonSetter("profileImageUrl")
    private void readProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageUrlSpecified = true;
    }

    public boolean isProfileImageUrlSpecified() {
        return profileImageUrlSpecified;
    }
}
