package com.crane.bandwebbackend.global.response;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 연쇄 처리 결과(성공 응답 effects). 해당하는 항목만 채움
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Effects {

    // API-081·082: 해제된 일정 연결
    @IdString
    private final List<Long> affectedScheduleIds;
    // API-072·073: TEAM_WITHDRAWN으로 바뀐 후보곡
    @IdString
    private final List<Long> withdrawnSongIds;
    // API-072: REJECTED가 된 참여 희망자
    @IdString
    private final List<Long> rejectedPerformanceMemberIds;
}
