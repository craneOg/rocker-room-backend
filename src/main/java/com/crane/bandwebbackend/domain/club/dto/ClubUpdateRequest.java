package com.crane.bandwebbackend.domain.club.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 16 요청. 보내지 않은 필드는 바꾸지 않는다. 필드마다 "보냈는지"를 기록해 명시적 null을 구분한다.
// name·isSearchable·clubType은 null 불가라 보내면서 null이면 서비스가 400으로 거부하고,
// description·profileImageKey·themeColor는 null이 허용된 값("지우기")이다. 값 검사는 서비스가 한다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClubUpdateRequest {

    private String name;
    private String description;

    @Getter(onMethod_ = @JsonIgnore)
    private Boolean searchable;

    private String clubType;
    private String clubTypeChangeReason;

    // API 129(purpose=CLUB_PROFILE)로 받은 임시 키
    private String profileImageKey;

    private String themeColor;

    // 서버 내부 상태다. getter를 통해 입력 속성으로 추론되지 않도록 @Getter(AccessLevel.NONE)을 붙인다
    @Getter(AccessLevel.NONE)
    private boolean nameSet;

    @Getter(AccessLevel.NONE)
    private boolean descriptionSet;

    @Getter(AccessLevel.NONE)
    private boolean searchableSet;

    @Getter(AccessLevel.NONE)
    private boolean clubTypeSet;

    @Getter(AccessLevel.NONE)
    private boolean profileImageKeySet;

    @Getter(AccessLevel.NONE)
    private boolean themeColorSet;

    @JsonSetter("name")
    private void readName(String name) {
        this.name = name;
        nameSet = true;
    }

    @JsonSetter("description")
    private void readDescription(String description) {
        this.description = description;
        descriptionSet = true;
    }

    // JSON 이름은 isSearchable이고 필드명(searchable)으로는 받지 않는다
    @JsonSetter("isSearchable")
    private void readSearchable(Boolean searchable) {
        this.searchable = searchable;
        searchableSet = true;
    }

    @JsonSetter("clubType")
    private void readClubType(String clubType) {
        this.clubType = clubType;
        clubTypeSet = true;
    }

    @JsonSetter("profileImageKey")
    private void readProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
        profileImageKeySet = true;
    }

    @JsonSetter("themeColor")
    private void readThemeColor(String themeColor) {
        this.themeColor = themeColor;
        themeColorSet = true;
    }

    public boolean hasName() {
        return nameSet;
    }

    public boolean hasDescription() {
        return descriptionSet;
    }

    public boolean hasSearchable() {
        return searchableSet;
    }

    public boolean hasClubType() {
        return clubTypeSet;
    }

    public boolean hasProfileImageKey() {
        return profileImageKeySet;
    }

    public boolean hasThemeColor() {
        return themeColorSet;
    }
}
