package com.crane.bandwebbackend.domain.song.dto;

import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.song.entity.WithdrawalType;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// 곡 공통 필드(API-075 카드, 077 song, 변경 응답 song)
@Getter
@Builder
public class SongResponse {

    @IdString
    private final Long id;

    private final SongStatus status;
    private final String title;
    private final String artist;
    private final String referenceUrl;
    private final String memo;
    // 후보 범위 팀, 공연 전체 범위면 NULL
    @IdString
    private final Long candidateTeamId;

    private final String candidateTeamName;

    @IdString
    private final Long assignedTeamId;

    private final String assignedTeamName;
    private final Proposer proposedBy;
    private final OffsetDateTime createdAt;
    private final Integer setlistOrder;
    private final String decisionNote;
    private final WithdrawalType withdrawalType;
    private final OffsetDateTime decidedAt;

    @IdString
    private final Long version;

    @Getter
    @Builder
    public static class Proposer {

        @IdString
        private final Long membershipId;

        private final String displayName;
    }

    public static SongResponse from(Song song) {
        Team candidate = song.getCandidateTeam();
        Team assigned = song.getAssignedTeam();
        return SongResponse.builder()
                .id(song.getId())
                .status(song.getStatus())
                .title(song.getTitle())
                .artist(song.getArtist())
                .referenceUrl(song.getReferenceUrl())
                .memo(song.getMemo())
                .candidateTeamId(candidate == null ? null : candidate.getId())
                .candidateTeamName(candidate == null ? null : candidate.getName())
                .assignedTeamId(assigned == null ? null : assigned.getId())
                .assignedTeamName(assigned == null ? null : assigned.getName())
                .proposedBy(Proposer.builder()
                        .membershipId(song.getProposedBy().getId())
                        .displayName(PerformanceAccessService.displayName(song.getProposedBy()))
                        .build())
                .createdAt(KstTime.toKst(song.getCreatedAt()))
                .setlistOrder(song.getSetlistOrder())
                .decisionNote(song.getDecisionNote())
                .withdrawalType(song.getWithdrawalType())
                .decidedAt(KstTime.toKst(song.getDecidedAt()))
                .version(song.getVersion())
                .build();
    }

    // GUEST가 본인 곡이 아닌 세트리스트 곡을 볼 때
    public static SongResponse minimal(Song song) {
        return SongResponse.builder()
                .id(song.getId())
                .status(song.getStatus())
                .title(song.getTitle())
                .artist(song.getArtist())
                .setlistOrder(song.getSetlistOrder())
                .build();
    }
}
