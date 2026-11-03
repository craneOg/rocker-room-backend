package com.crane.bandwebbackend.domain.song.dto;

import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 곡 API 요청 본문. 연주자·가불·투표의 사람은 performanceMemberId
public final class SongRequests {

    private SongRequests() {}

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Session {

        @NotNull
        @IdString
        private Long performanceMemberId;

        @NotNull
        @IdString
        private Long partId;
    }

    /**
     * 확정 입력(API-076 직접 확정, 080). LOCKED 공연은 네 값 모두 필수(X-01, X-05).
     * assignedTeamId: 생략 = 후보 팀이 APPROVED면 후보 팀, null 명시 = 담당 팀 없음 → 생략과 null을 구분
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public abstract static class ConfirmFields {

        @Positive
        private Integer setlistOrder;

        @IdString
        private Long assignedTeamId;

        @Getter(AccessLevel.NONE)
        private boolean assignedTeamIdPresent;

        // null = 생략(연주자 변경 없음)
        private List<@NotNull @Valid Session> sessions;

        // decision_note에 최근 사유로 남김. LOCKED면 필수
        @Size(max = 500)
        private String reason;

        @JsonSetter("assignedTeamId")
        void assignedTeamId(Long value) {
            this.assignedTeamId = value;
            this.assignedTeamIdPresent = true;
        }

        public boolean hasAssignedTeamId() {
            return assignedTeamIdPresent;
        }
    }

    // API-076 후보곡 등록·직접 확정
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Create extends ConfirmFields {

        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 200)
        private String artist;

        // https만(규칙 0.5). 서버는 가져오지 않고 저장만
        private String referenceUrl;

        @Size(max = 500)
        private String memo;

        // 값 있으면 팀 범위 후보. 등록 뒤 변경 불가
        @IdString
        private Long candidateTeamId;

        // PROPOSED(기본) 또는 CONFIRMED(A만)
        private SongStatus status;
    }

    // API-080
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Confirm extends ConfirmFields {

        @NotNull
        @IdString
        private Long version;
    }

    // API-078: 보낸 필드만 변경(null을 보내면 비움, title 제외)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Update {

        @NotNull
        @IdString
        private Long version;

        private String title;
        private String artist;
        private String referenceUrl;
        private String memo;

        @Getter(AccessLevel.NONE)
        private boolean titleSet;

        @Getter(AccessLevel.NONE)
        private boolean artistSet;

        @Getter(AccessLevel.NONE)
        private boolean referenceUrlSet;

        @Getter(AccessLevel.NONE)
        private boolean memoSet;

        @JsonSetter("title")
        void title(String v) {
            title = v;
            titleSet = true;
        }

        @JsonSetter("artist")
        void artist(String v) {
            artist = v;
            artistSet = true;
        }

        @JsonSetter("referenceUrl")
        void referenceUrl(String v) {
            referenceUrl = v;
            referenceUrlSet = true;
        }

        @JsonSetter("memo")
        void memo(String v) {
            memo = v;
            memoSet = true;
        }

        public boolean hasTitle() {
            return titleSet;
        }

        public boolean hasArtist() {
            return artistSet;
        }

        public boolean hasReferenceUrl() {
            return referenceUrlSet;
        }

        public boolean hasMemo() {
            return memoSet;
        }
    }

    // 확정 취소·제외·철회·재검토·임원 삭제 공통(API-081~083, 156, 157)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Decision {

        @NotNull
        @IdString
        private Long version;

        // 감사 기록 사유. LOCKED 공연이면 필수(X-02)
        @Size(max = 500)
        private String reason;

        // 결정 메모(decision_note). 선택이지만 입력 권장
        @Size(max = 500)
        private String decisionNote;

        // 확인 필요 규칙(0.1)
        private boolean confirmed;

        // API-081: 투표·가불 응답 삭제
        private boolean resetEvaluations;
    }

    // API-084. null = 담당 팀 해제(LOCKED 불가)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AssignTeam {

        @NotNull
        @IdString
        private Long version;

        @IdString
        private Long assignedTeamId;

        @Size(max = 500)
        private String reason;
    }

    // API-085 연주자 전체 저장
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Sessions {

        @NotNull
        @IdString
        private Long version;

        @NotNull
        private List<@NotNull @Valid Session> sessions;

        @Size(max = 500)
        private String reason;
    }

    // API-079 필요 편성 전체 저장. 빈 배열 = 편성 미지정
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Requirements {

        @NotNull
        @IdString
        private Long version;

        @NotNull
        private List<@NotNull @Valid Requirement> requirements;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Requirement {

        @NotNull
        @IdString
        private Long partId;

        @NotNull
        @Positive
        private Integer requiredCount;
    }
}
