package com.crane.bandwebbackend.domain.part.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PartResponseTest {
    @Test
    void part01UsesIsActiveField() {
        PartResponse response = PartResponse.builder().id("1").clubId("2").name("기타")
                .displayOrder(0).active(true).revision("part-1").build();

        JsonNode body = new ObjectMapper().valueToTree(response);

        assertThat(body.path("isActive").asBoolean()).isTrue();
        assertThat(body.has("active")).isFalse();
    }
}
