package com.crane.bandwebbackend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 121 요청: 후임 소속 ID, 사유(1~500자), 활동 임원 우회 여부. 해제할 임원은 받지 않고 서버가 기존 임원 전체를 해제한다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminReplaceRequest {

    @NotNull
    private String newAdminMembershipId;

    // ClubStatusChangeRequest와 같은 규칙: 유니코드 공백뿐인 사유도 거른다
    @NotBlank
    @Pattern(regexp = "(?sU).*\\S.*")
    @Size(max = 500)
    private String reason;

    // 운영자 우회: true면 활동(ACTIVE) 임원이 있어도 교체한다(그 임원도 해제). 없거나 false면 422
    private boolean overrideActiveAdmin;
}
