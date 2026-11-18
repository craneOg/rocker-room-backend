package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 121 응답: 교체 뒤 임원(후임 1명)
@Getter
@Builder
public class AdminReplaceResponse {
    private final List<MembershipResponse> admins;
}
