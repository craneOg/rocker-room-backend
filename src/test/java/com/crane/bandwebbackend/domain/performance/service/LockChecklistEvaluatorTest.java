package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.AuditionRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.ParticipantRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.PlayerRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.RequirementRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.Snapshot;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.SongRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.TeamRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LockChecklistEvaluatorTest {
    @Test
    void allSatisfiedAndGappedOrdersAreNormalized() {
        var result = LockChecklistEvaluator.evaluate(new Snapshot(
                List.of(new SongRow(2, 4, 12L, "APPROVED"), new SongRow(1, 2, 11L, "APPROVED")),
                List.of(new PlayerRow(1, 21, 3, "APPROVED"), new PlayerRow(2, 22, 3, "APPROVED")),
                List.of(),
                List.of(new ParticipantRow(21, "APPROVED"), new ParticipantRow(22, "APPROVED")),
                List.of(new RequirementRow(1, 3, "드럼", 1)),
                List.of(),
                2));

        assertThat(result.getHardErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getItems())
                .hasSize(11)
                .allMatch(item -> item.getResult().name().equals("SATISFIED"));
        assertThat(result.getNormalizedSetlist())
                .extracting(row -> row.getSongId() + ":" + row.getOrder())
                .containsExactly("1:1", "2:2");
    }

    @Test
    void allSixBlockingRulesAreEvaluatedTogether() {
        var result = LockChecklistEvaluator.evaluate(new Snapshot(
                List.of(new SongRow(1, null, null, null)),
                List.of(),
                List.of(new TeamRow(5, "SUBMITTED")),
                List.of(new ParticipantRow(8, "APPLIED")),
                List.of(),
                List.of(new AuditionRow(9, 1, null, null, LocalDateTime.parse("2026-11-01T10:00:00"))),
                null));

        assertThat(result.getHardErrors())
                .extracting(item -> item.getCheckId())
                .containsExactly("L-01", "L-02", "L-03", "L-04", "L-05", "L-06");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void warningsIncludeDeficitEmptyParticipantLimitConsecutivePlayerAndLatestFailedAudition() {
        var result = LockChecklistEvaluator.evaluate(new Snapshot(
                List.of(new SongRow(1, 1, 11L, "APPROVED"), new SongRow(2, 2, 12L, "APPROVED")),
                List.of(new PlayerRow(1, 21, 3, "APPROVED"), new PlayerRow(2, 21, 3, "APPROVED")),
                List.of(),
                List.of(new ParticipantRow(21, "APPROVED"), new ParticipantRow(22, "APPROVED")),
                List.of(new RequirementRow(1, 3, "드럼", 2)),
                List.of(
                        new AuditionRow(30, 2, null, "PASSED", LocalDateTime.parse("2026-11-01T10:00:00")),
                        new AuditionRow(31, 2, null, "FAILED", LocalDateTime.parse("2026-11-02T10:00:00"))),
                1));

        assertThat(result.getHardErrors()).isEmpty();
        assertThat(result.getWarnings())
                .extracting(item -> item.getCheckId())
                .containsExactly("L-07", "L-08", "L-09", "L-10", "L-11");
    }

    @Test
    void latestPassedSongReviewClearsFailedReviewWarning() {
        var result = LockChecklistEvaluator.evaluate(new Snapshot(
                List.of(new SongRow(1, 1, 11L, "APPROVED")),
                List.of(new PlayerRow(1, 21, 3, "APPROVED")),
                List.of(),
                List.of(new ParticipantRow(21, "APPROVED")),
                List.of(),
                List.of(
                        new AuditionRow(30, 1, null, "FAILED", LocalDateTime.parse("2026-11-01T10:00:00")),
                        new AuditionRow(31, 1, null, "PASSED", LocalDateTime.parse("2026-11-02T10:00:00"))),
                1));

        assertThat(result.getWarnings()).noneMatch(item -> item.getCheckId().equals("L-11"));
    }
}
