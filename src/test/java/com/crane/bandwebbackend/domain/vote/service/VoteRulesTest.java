package com.crane.bandwebbackend.domain.vote.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteRulesTest {

    @Test
    @DisplayName("V-04 찬성 방식은 1만, 점수 방식은 1~5")
    void scoreByMethod() {
        assertThatCode(() -> VoteService.validateScore(VoteMethod.APPROVAL, 1)).doesNotThrowAnyException();
        assertThatCode(() -> VoteService.validateScore(VoteMethod.SCORE, 5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> VoteService.validateScore(VoteMethod.APPROVAL, 3))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> VoteService.validateScore(VoteMethod.SCORE, 0)).isInstanceOf(BusinessException.class);
    }
}
