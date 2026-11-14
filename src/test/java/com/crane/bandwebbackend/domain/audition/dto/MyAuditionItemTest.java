package com.crane.bandwebbackend.domain.audition.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MyAuditionItemTest {
    @Test
    void latestFlagUsesApi150FieldName() throws Exception {
        // given
        MyAuditionItem item = MyAuditionItem.builder().isLatest(true).build();

        // when
        String json = new ObjectMapper().writeValueAsString(item);

        // then
        assertThat(json).contains("\"isLatest\":true");
        assertThat(json).doesNotContain("\"latest\":");
    }
}
