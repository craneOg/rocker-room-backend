package com.crane.bandwebbackend.domain.team.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamRulesTest {

    @Test
    @DisplayName("T-07 비어 있는 가장 작은 번호 \"n팀\" (모든 상태 팀 이름 기준)")
    void smallestFreeNumber() {
        assertThat(TeamService.nextDefaultName(Set.of())).isEqualTo("1팀");
        assertThat(TeamService.nextDefaultName(Set.of("1팀", "3팀", "보컬팀"))).isEqualTo("2팀");
    }
}
