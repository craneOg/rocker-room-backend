package com.crane.bandwebbackend.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DtoClassContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contracts")
    @DisplayName("agent.md §6 DTO 일반 클래스 전환은 기존 JSON 필드·값과 검증 조건을 보존한다")
    void preservesRecordContract(String className, JsonNode contract) throws Exception {
        Class<?> type = Class.forName(className);
        assertThat(type.isRecord()).isFalse();
        // 기준값은 전환 전 실제 record 직렬화·Bean Validation 결과를 수집한 것이다.
        Object value = MAPPER.treeToValue(contract.get("json"), type);
        JsonNode serialized = MAPPER.readTree(MAPPER.writeValueAsBytes(value));
        assertThat(serialized).isEqualTo(contract.get("json"));
        Object empty = MAPPER.readValue("{}", type);
        var violations = VALIDATORS.getValidator().validate(empty).stream()
                .map(v -> v.getPropertyPath() + ":"
                        + v.getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName())
                .sorted()
                .toList();
        JsonNode serializedViolations = MAPPER.valueToTree(violations);
        assertThat(serializedViolations).isEqualTo(contract.get("violations"));
        assertThat(type.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("toString"));
        assertThat(type.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }

    static Stream<Arguments> contracts() throws Exception {
        try (var input = DtoClassContractTest.class.getResourceAsStream("/dto-record-contracts.json")) {
            JsonNode contracts = MAPPER.readTree(input);
            var arguments = new ArrayList<Arguments>();
            contracts.properties().forEach(entry -> arguments.add(Arguments.of(entry.getKey(), entry.getValue())));
            return arguments.stream();
        }
    }
}
