package com.crane.bandwebbackend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 120·159 요청: 사유는 필수(1~500자)이며 감사 로그에 기록한다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClubStatusChangeRequest {

    // @NotBlank는 U+2003 같은 유니코드 공백을 못 거른다. strip() 후 빈 값이 되지 않도록 (?U)로 공백이 아닌 글자를 요구한다
    @NotBlank
    @Pattern(regexp = "(?sU).*\\S.*")
    @Size(max = 500)
    private String reason;
}
