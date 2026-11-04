package com.crane.bandwebbackend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 8 요청. 보내지 않은 필드는 바꾸지 않는다. 필드마다 "보냈는지"를 기록해 명시적 null을 구분한다.
// name·handle·profileImagePublic은 null 불가라 보내면서 null이면 서비스가 400으로 거부하고,
// profileImageUrl만 null이 허용된 값("사진 삭제")이다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateProfileRequest {

    @Size(min = 1, max = 50)
    private String name;

    @Pattern(regexp = "^[a-z0-9_]{5,30}$")
    private String handle;

    // API 129로 받은 임시 키. 요청 필드명은 명세대로 profileImageUrl이다
    private String profileImageUrl;

    private Boolean profileImagePublic;

    private boolean nameSpecified;
    private boolean handleSpecified;
    private boolean profileImageUrlSpecified;
    private boolean profileImagePublicSpecified;

    @JsonSetter("name")
    private void readName(String name) {
        this.name = name;
        this.nameSpecified = true;
    }

    @JsonSetter("handle")
    private void readHandle(String handle) {
        this.handle = handle;
        this.handleSpecified = true;
    }

    @JsonSetter("profileImageUrl")
    private void readProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageUrlSpecified = true;
    }

    @JsonSetter("profileImagePublic")
    private void readProfileImagePublic(Boolean profileImagePublic) {
        this.profileImagePublic = profileImagePublic;
        this.profileImagePublicSpecified = true;
    }

    public boolean isNameSpecified() {
        return nameSpecified;
    }

    public boolean isHandleSpecified() {
        return handleSpecified;
    }

    public boolean isProfileImageUrlSpecified() {
        return profileImageUrlSpecified;
    }

    public boolean isProfileImagePublicSpecified() {
        return profileImagePublicSpecified;
    }
}
