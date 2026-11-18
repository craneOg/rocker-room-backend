package com.crane.bandwebbackend.domain.club.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 18 요청. reason은 선택이고 null 불가라, 보내지 않은 것과 null을 구분하려고 "보냈는지"를 기록한다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JoinCodeRegenerateRequest {

    private String reason;

    private boolean reasonSpecified;

    @JsonSetter("reason")
    private void readReason(String reason) {
        this.reason = reason;
        this.reasonSpecified = true;
    }

    @JsonIgnore
    public boolean isReasonSpecified() {
        return reasonSpecified;
    }
}
