package com.crane.bandwebbackend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 변경 응답 공통 틀: {resource, warnings, effects?}
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MutationResult<T> {

    private final T resource;
    private final List<Warning> warnings;
    private final Effects effects;
}
