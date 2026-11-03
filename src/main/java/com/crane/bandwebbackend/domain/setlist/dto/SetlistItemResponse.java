package com.crane.bandwebbackend.domain.setlist.dto;

import com.crane.bandwebbackend.domain.song.dto.SongViews;
import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-087 항목. GUEST에게 본인 곡이 아니면 sessions는 빈 목록, 팀 정보·version은 NULL
@Getter
@Builder
public class SetlistItemResponse {

    private final Song song;
    private final List<SongViews.Session> sessions;

    @Getter
    @Builder
    public static class Song {

        @IdString
        private final Long id;

        private final String title;
        private final String artist;
        // NULL = 미배정
        private final Integer setlistOrder;

        @IdString
        private final Long assignedTeamId;

        private final String assignedTeamName;

        @IdString
        private final Long version;
    }
}
