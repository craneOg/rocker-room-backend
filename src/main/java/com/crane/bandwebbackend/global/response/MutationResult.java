package com.crane.bandwebbackend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 변경 응답 공통 틀: {resource, warnings, effects}. effects는 명세상 필수라 연쇄 처리가 없으면 {}
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MutationResult<T> {

    private static final Effects NO_EFFECTS = Effects.builder().build();

    private final T resource;
    private final List<Warning> warnings;
    private final Effects effects;

    public Effects getEffects() {
        return effects == null ? NO_EFFECTS : effects;
    }
}
