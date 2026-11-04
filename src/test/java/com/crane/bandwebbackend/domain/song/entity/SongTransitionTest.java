package com.crane.bandwebbackend.domain.song.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.team.entity.TeamSource;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SongTransitionTest {

    private final Team team = Team.createApproved(null, "1팀", TeamSource.ADMIN, null);

    private Song proposed() {
        return Song.propose(null, null, "곡", null, null, null, null);
    }

    @Test
    @DisplayName("X-05 확정은 담당 팀·순서·사유를 한 번에 기록")
    void confirm() {
        Song song = proposed();
        song.confirm(team, 3, "투표 1위");

        assertThat(song.getStatus()).isEqualTo(SongStatus.CONFIRMED);
        assertThat(song.getAssignedTeam()).isSameAs(team);
        assertThat(song.getSetlistOrder()).isEqualTo(3);
        assertThat(song.getDecisionNote()).isEqualTo("투표 1위");
        assertThat(song.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("API-080 assignedTeamId null 명시 = 담당 팀 없이 확정")
    void confirmWithoutTeam() {
        Song song = Song.propose(null, team, "곡", null, null, null, null);
        song.confirm(null, null, null);
        assertThat(song.getAssignedTeam()).isNull();
    }

    @Test
    @DisplayName("SONG-003 9-4 CONFIRMED → REJECTED 한 번에, 담당 팀·순서 해제")
    void rejectConfirmed() {
        Song song = proposed();
        song.confirm(team, 1, null);
        song.reject("편성 불가");

        assertThat(song.getStatus()).isEqualTo(SongStatus.REJECTED);
        assertThat(song.getAssignedTeam()).isNull();
        assertThat(song.getSetlistOrder()).isNull();
    }

    @Test
    @DisplayName("SONG-003 9-5 재검토 REJECTED → PROPOSED")
    void reconsider() {
        Song song = proposed();
        song.reject("편성 불가");
        song.reconsider();
        assertThat(song.getStatus()).isEqualTo(SongStatus.PROPOSED);
        assertThat(song.getDecisionNote()).isEqualTo("편성 불가");
    }

    @Test
    @DisplayName("재검토는 REJECTED에서만 409")
    void reconsiderOnlyFromRejected() {
        assertThatThrownBy(() -> proposed().reconsider()).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("S-11 철회·임원 삭제는 PROPOSED에서만, withdrawalType 기록")
    void withdraw() {
        Song song = proposed();
        song.withdraw(WithdrawalType.ADMIN);
        assertThat(song.getStatus()).isEqualTo(SongStatus.WITHDRAWN);
        assertThat(song.getWithdrawalType()).isEqualTo(WithdrawalType.ADMIN);

        Song confirmed = proposed();
        confirmed.confirm(team, null, null);
        assertThatThrownBy(() -> confirmed.withdraw(WithdrawalType.PROPOSER)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("세트리스트 순서는 CONFIRMED만, 미배정(NULL)은 언제나 허용")
    void setlistOrderOnlyConfirmed() {
        Song song = proposed();
        song.changeSetlistOrder(null);
        assertThatThrownBy(() -> song.changeSetlistOrder(1)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("화면이 본 버전과 다르면 VERSION_CONFLICT")
    void verifyVersion() {
        Song song = proposed();
        ReflectionTestUtils.setField(song, "version", 2L);
        song.verifyVersion(2L);
        assertThatThrownBy(() -> song.verifyVersion(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }
}
