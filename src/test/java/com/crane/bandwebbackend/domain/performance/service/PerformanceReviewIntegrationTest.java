package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.performance.dto.VoteMethodChangeRequest;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql", "/performance-review-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PerformanceReviewIntegrationTest {
	@Autowired private PerformanceDetailReader details;
	@Autowired private PerformancePolicyEditService policies;
	@Autowired private VoteMethodChangeService votes;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	@Test
	@DisplayName("API-41 객원 공연 상세에는 본인이 참석하는 다음 확정 일정만 표시한다")
	void guestSeesOnlyAttendedNextSchedule() {
		var detail = details.read(1L, 1L, 4L);

		assertThat(detail.nextSchedule()).isNotNull();
		assertThat(detail.nextSchedule().id()).isEqualTo("11");
	}

	@Test
	@DisplayName("API-42 정책 변경 응답 버전으로 다음 PATCH가 성공한다")
	void policyUpdateReturnsCommittedVersion() throws Exception {
		var response = policies.update(1L, 1L, 1L,
				mapper.readTree("{\"version\":\"0\",\"voteMaxChoices\":2,"
						+ "\"practiceSeasonStart\":\"2026-12-01\",\"practiceSeasonEnd\":\"2026-12-31\"}"));

		assertThat(Long.parseLong(response.policy().performanceVersion()))
				.isEqualTo(versionInDatabase()).isGreaterThan(0);
		var next = policies.update(1L, 1L, 1L, mapper.readTree(
				"{\"version\":\"" + response.policy().performanceVersion() + "\",\"voteMaxChoices\":3}"));
		assertThat(Long.parseLong(next.policy().performanceVersion()))
				.isEqualTo(versionInDatabase()).isGreaterThan(Long.parseLong(response.policy().performanceVersion()));
	}

	@Test
	@DisplayName("API-44 투표 방식 변경 응답 버전으로 다음 변경이 성공한다")
	void voteMethodChangeReturnsCommittedVersion() {
		var response = votes.change(1L, 1L, 1L,
				new VoteMethodChangeRequest("0", VoteMethod.SCORE, true, "방식 변경"));

		assertThat(Long.parseLong(response.policy().performanceVersion()))
				.isEqualTo(versionInDatabase()).isGreaterThan(0);
		var next = votes.change(1L, 1L, 1L, new VoteMethodChangeRequest(
				response.policy().performanceVersion(), VoteMethod.APPROVAL, true, "방식 재변경"));
		assertThat(Long.parseLong(next.policy().performanceVersion()))
				.isEqualTo(versionInDatabase()).isGreaterThan(Long.parseLong(response.policy().performanceVersion()));
	}

	private long versionInDatabase() {
		return jdbc.queryForObject("SELECT version FROM performance WHERE id = 1", Long.class);
	}
}
