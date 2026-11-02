package com.crane.bandwebbackend.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class AuditRecorderTest {

    @Autowired
    AuditRecorder auditRecorder;

    @Autowired
    AuditTestService service;

    @Autowired
    AuditLogRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TransactionTemplate txTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("U10 트랜잭션 안에서 record하면 컬럼·detail JSON·created_at이 저장된다")
    void savesInTransaction() throws Exception {
        Map<String, Object> detail = Map.of("before", Map.of("role", "MEMBER"), "after", Map.of("role", "CLUB_ADMIN"));

        service.recordInTx("ADMIN_GRANTED", "사유", detail);

        AuditLog saved = repository.findAll().get(0);
        assertThat(saved.getClubId()).isEqualTo(1L);
        assertThat(saved.getActorUserId()).isEqualTo(2L);
        assertThat(saved.getAction()).isEqualTo("ADMIN_GRANTED");
        assertThat(saved.getTargetType()).isEqualTo("CLUB");
        assertThat(saved.getTargetId()).isEqualTo(3L);
        assertThat(saved.getReason()).isEqualTo("사유");
        assertThat(saved.getCreatedAt()).isNotNull();
        JsonNode json = objectMapper.readTree(saved.getDetail());
        assertThat(json.at("/before/role").asText()).isEqualTo("MEMBER");
        assertThat(json.at("/after/role").asText()).isEqualTo("CLUB_ADMIN");
    }

    @Test
    @DisplayName("U10 트랜잭션 밖에서 호출하면 IllegalTransactionStateException")
    void failsWithoutTransaction() {
        assertThatThrownBy(() -> auditRecorder.record(1L, 2L, "A", "CLUB", 3L, null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("U10 서비스 트랜잭션이 롤백되면 감사 행도 롤백된다")
    void rollsBackWithService() {
        assertThatThrownBy(service::recordThenFail).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("U-09 금지 키는 최상위·중첩 Map·List 안 Map 어디서든 거부하고 메시지에 값을 쓰지 않는다")
    void rejectsForbiddenKeys() {
        assertRejected(Map.of("name", "홍길동"));
        assertRejected(Map.of("after", Map.of("email", "secret@x.com")));
        assertRejected(Map.of("items", List.of(Map.of("ip", "1.2.3.4"))));
        assertRejected(Map.of("providerUid", "v"));
        assertRejected(Map.of("displayName", "v"));
        assertRejected(Map.of("handle", "v"));
        assertThatThrownBy(() -> service.recordInTx("A", null, Map.of("email", "secret@x.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email")
                .hasMessageNotContaining("secret@x.com");
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NAME", "Email", "PROVIDERUID", "tokenHash", "Token", "TOKEN_ID", "tokenVersion"})
    @DisplayName("U-09 대소문자 변형과 token으로 시작하는 키도 거부한다")
    void rejectsCaseVariantsAndTokenPrefix(String key) {
        assertRejected(Map.of(key, "v"));
    }

    @Test
    @DisplayName("U-09 ID와 변경 전·후 값 키는 허용된다")
    void allowsIdAndBeforeAfter() {
        assertThatCode(() ->
                        service.recordInTx("A", null, Map.of("targetUserId", 5, "before", Map.of("role", "MEMBER"))))
                .doesNotThrowAnyException();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("U-09 action·targetType 필수와 길이, reason 길이를 검증한다")
    void validatesLengthsAndRequired() {
        assertThatThrownBy(() -> service.recordInTx(null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordInTx("", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordInTx("A".repeat(51), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordInTx("A", "r".repeat(501), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inTx(() -> auditRecorder.record(1L, 2L, "A", null, 3L, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inTx(() -> auditRecorder.record(1L, 2L, "A", "T".repeat(31), 3L, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> service.recordInTx("A".repeat(50), "r".repeat(500), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U-09 detail이 null이거나 비어 있으면 detail은 NULL로 저장된다")
    void nullOrEmptyDetailStoresNull() {
        service.recordInTx("A", null, null);
        service.recordInTx("B", null, Map.of());

        assertThat(repository.findAll()).hasSize(2).allMatch(l -> l.getDetail() == null);
    }

    private void assertRejected(Map<String, Object> detail) {
        assertThatThrownBy(() -> service.recordInTx("A", null, detail)).isInstanceOf(IllegalArgumentException.class);
    }

    private void inTx(Runnable r) {
        // recordInTx는 targetType 고정이라, 검증 대상 인자를 바꾸려면 트랜잭션을 직접 열어야 한다
        txTemplate.executeWithoutResult(s -> r.run());
    }
}
