package com.crane.bandwebbackend;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.part.controller.PartController;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.service.BandPerformanceInitializationService;
import com.crane.bandwebbackend.domain.performance.service.LockReadinessReader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BandWebBackendApplicationTests {

    @Autowired
    PracticeScheduleRepository scheduleRepository;

    @Autowired
    PartRepository partRepository;

    @Autowired
    LockReadinessReader lockReadinessReader;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MembershipWithdrawalService membershipWithdrawalService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BandPerformanceInitializationService bandPerformanceInitializationService;

    @Autowired
    EntityManager entityManager;

    @Test
    void contextLoads() {
    }

    @Test
    void sch01AudienceQueryRunsAgainstMigratedSchema() {
        assertThat(scheduleRepository.findAttendedScheduleIds(-1L, -1L)).isEmpty();
    }

    @Test
    void schedulePageFetchGraphRunsAgainstMigratedSchema() {
        // given
        var page = PageRequest.of(0, 20);

        // when
        var result = scheduleRepository.findAll(
                (root, query, builder) -> builder.equal(root.get("id"), -1L), page);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void authenticatedRoutesStayDisabledUntilIntegration() {
        assertThat(applicationContext.getBeansOfType(PartController.class)).isEmpty();
    }

    @Test
    void part01HistoricalReferenceQueryRunsAgainstMigratedSchema() {
        assertThat(partRepository.findHistoricallyReferencedPartIds(-1L)).isEmpty();
    }

    @Test
    void lockReadinessQueriesRunAgainstMigratedSchema() {
        var snapshot = lockReadinessReader.read(-1L);
        assertThat(snapshot.songs()).isEmpty();
        assertThat(snapshot.players()).isEmpty();
        assertThat(snapshot.auditions()).isEmpty();
    }

    @Test
    @Transactional
    void memberCanWithdrawWithoutAnExternalSelfWithdrawalPort() {
        insertUser(910001L, "withdraw_admin");
        insertUser(910002L, "withdraw_member");
        insertClub(910001L, "withdraw-member-club", 910001L);
        insertMembership(910001L, 910001L, 910001L, "CLUB_ADMIN");
        insertMembership(910002L, 910001L, 910002L, "MEMBER");

        var result = membershipWithdrawalService.withdraw(910001L, 910002L, 910002L, null);

        assertThat(result.resource().status().name()).isEqualTo("WITHDRAWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM membership WHERE id = 910002", String.class))
                .isEqualTo("WITHDRAWN");
    }

    @Test
    @Transactional
    void lastAdminWithdrawalDeactivatesClubWithoutAnExternalSuccessionPort() {
        insertUser(920001L, "last_admin");
        insertClub(920001L, "last-admin-club", 920001L);
        insertMembership(920001L, 920001L, 920001L, "CLUB_ADMIN");

        membershipWithdrawalService.withdraw(920001L, 920001L, 920001L, null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM club WHERE id = 920001", String.class))
                .isEqualTo("INACTIVE");
    }

    @Test
    @Transactional
    void lastAdminWithdrawalPromotesTheEarliestEligibleMember() {
        insertUser(930001L, "promote_admin");
        insertUser(930002L, "promote_member");
        insertClub(930001L, "promote-member-club", 930001L);
        insertMembership(930001L, 930001L, 930001L, "CLUB_ADMIN");
        insertMembership(930002L, 930001L, 930002L, "MEMBER");

        membershipWithdrawalService.withdraw(930001L, 930001L, 930001L, null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM membership WHERE id = 930002", String.class))
                .isEqualTo("CLUB_ADMIN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM club WHERE id = 930001", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    @Transactional
    void bandPerformanceInitializationCreatesParticipantsAndDefaultTeam() {
        insertUser(940001L, "band_admin");
        insertUser(940002L, "band_member");
        insertClub(940001L, "band-initialization-club", 940001L);
        insertMembership(940001L, 940001L, 940001L, "CLUB_ADMIN");
        insertMembership(940002L, 940001L, 940002L, "MEMBER");
        jdbcTemplate.update("""
                INSERT INTO performance
                    (id, club_id, title, preset, status, created_by_user_id, version)
                VALUES (940001, 940001, 'BAND 초기화 테스트', 'BAND', 'DRAFT', 940001, 0)
                """);
        Performance performance = entityManager.find(Performance.class, 940001L);

        bandPerformanceInitializationService.initialize(performance, 940001L);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM performance_member WHERE performance_id = 940001
                """, Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM team_member WHERE performance_id = 940001
                """, Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT pm.membership_id FROM team t
                JOIN performance_member pm ON pm.id = t.leader_member_id
                WHERE t.performance_id = 940001 AND t.is_default = TRUE
                """, Long.class)).isEqualTo(940001L);
    }

    /** 탈퇴 통합 테스트용 활성 사용자를 저장한다. */
    private void insertUser(long id, String handle) {
        jdbcTemplate.update("""
                INSERT INTO users (id, name, handle, status, is_super_admin, token_version)
                VALUES (?, ?, ?, 'ACTIVE', FALSE, 0)
                """, id, handle, handle);
    }

    /** 탈퇴 통합 테스트용 활성 동아리를 저장한다. */
    private void insertClub(long id, String name, long creatorId) {
        jdbcTemplate.update("""
                INSERT INTO club (id, name, club_type, status, is_searchable, created_by)
                VALUES (?, ?, 'CLUB', 'ACTIVE', FALSE, ?)
                """, id, name, creatorId);
    }

    /** 탈퇴 통합 테스트용 활성 소속을 저장한다. */
    private void insertMembership(long id, long clubId, long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO membership
                    (id, club_id, user_id, member_type, role, status, join_source, approved_at)
                VALUES (?, ?, ?, 'REGULAR', ?, 'ACTIVE', 'CLUB_CREATOR', UTC_TIMESTAMP(6))
                """, id, clubId, userId, role);
    }

}
