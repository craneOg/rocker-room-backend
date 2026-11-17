package com.crane.bandwebbackend.domain.home.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClubAuditLogApiTest extends HomeApiTestBase {

    private void audit(Long club, String action, String targetType, Long targetId, String createdAt) {
        jdbc.update(
                "INSERT INTO audit_log (club_id, actor_user_id, action, target_type, target_id, reason, detail,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, '사유', '{\"a\":1}', ?)",
                club,
                adminUser,
                action,
                targetType,
                targetId,
                createdAt);
    }

    @Test
    @DisplayName("API 116: 이 동아리 기록만 최근순으로, reason·detail·clubId 없이")
    void listShape() throws Exception {
        audit(clubId, "A_FIRST", "CLUB", clubId, "2026-10-01 00:00:00");
        audit(clubId, "B_SECOND", "MEMBERSHIP", 55L, "2026-10-02 00:00:00");
        audit(clubId + 1000, "OTHER_CLUB", "CLUB", 1L, "2026-10-03 00:00:00");

        mockMvc.perform(get(path("/audit-logs")).cookie(access(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].action").value("B_SECOND"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("MEMBERSHIP"))
                .andExpect(jsonPath("$.data.items[0].targetId").value("55"))
                .andExpect(jsonPath("$.data.items[0].actorUserId").value(String.valueOf(adminUser)))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-02T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].detail").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].clubId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].action").value("A_FIRST"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("API 116: action·targetType·targetId·기간 필터와 페이지, 잘못된 값은 400")
    void filtersAndPaging() throws Exception {
        audit(clubId, "X", "CLUB", 1L, "2026-10-01 00:00:00");
        audit(clubId, "Y", "MEMBERSHIP", 55L, "2026-10-02 00:00:00");
        audit(clubId, "Z", "MEMBERSHIP", 56L, "2026-10-03 00:00:00");

        mockMvc.perform(get(path("/audit-logs")).param("action", "X, Y").cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(get(path("/audit-logs"))
                        .param("targetType", "MEMBERSHIP")
                        .cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(get(path("/audit-logs")).param("targetId", "56").cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.items[0].action").value("Z"));
        mockMvc.perform(get(path("/audit-logs"))
                        .param("actorUserId", String.valueOf(adminUser))
                        .param("from", "2026-10-02T09:00:00+09:00")
                        .param("to", "2026-10-02T09:00:00+09:00")
                        .cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mockMvc.perform(get(path("/audit-logs"))
                        .param("size", "2")
                        .param("page", "1")
                        .cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mockMvc.perform(get(path("/audit-logs")).param("size", "0").cookie(access(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("/audit-logs")).param("targetId", "abc").cookie(access(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path("/audit-logs"))
                        .param("from", "2026-10-03T00:00:00+09:00")
                        .param("to", "2026-10-01T00:00:00+09:00")
                        .cookie(access(adminUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API 116: 임원이 아니면 403, 소속이 없으면 404. API 125는 그대로 동작")
    void authAndApi125() throws Exception {
        audit(clubId, "X", "CLUB", 1L, "2026-10-01 00:00:00");
        mockMvc.perform(get(path("/audit-logs")).cookie(access(memberUser))).andExpect(status().isForbidden());
        mockMvc.perform(get(path("/audit-logs")).cookie(access(user()))).andExpect(status().isNotFound());

        jdbc.update("UPDATE users SET is_super_admin = TRUE WHERE id = ?", adminUser);
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("clubId", String.valueOf(clubId))
                        .cookie(access(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].reason").value("사유"));
    }
}
