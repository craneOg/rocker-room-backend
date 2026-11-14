package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.performance.dto.LockChecklistItem;
import com.crane.bandwebbackend.domain.performance.dto.LockValidationResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** L-01~L-11의 판정은 DB 접근 없이 수행해 검사·확정·홈 점검이 동일한 결과를 쓴다. */
public final class LockChecklistEvaluator {
    /** 정적 평가기이므로 인스턴스 생성을 막는다. */
    private LockChecklistEvaluator() {}

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class SongRow {
        @JsonProperty("id")
        private long id;

        @JsonProperty("order")
        private Integer order;

        @JsonProperty("assignedTeamId")
        private Long assignedTeamId;

        @JsonProperty("assignedTeamStatus")
        private String assignedTeamStatus;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class PlayerRow {
        @JsonProperty("songId")
        private long songId;

        @JsonProperty("performanceMemberId")
        private long performanceMemberId;

        @JsonProperty("partId")
        private long partId;

        @JsonProperty("memberStatus")
        private String memberStatus;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class TeamRow {
        @JsonProperty("id")
        private long id;

        @JsonProperty("status")
        private String status;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class ParticipantRow {
        @JsonProperty("id")
        private long id;

        @JsonProperty("status")
        private String status;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class RequirementRow {
        @JsonProperty("songId")
        private long songId;

        @JsonProperty("partId")
        private long partId;

        @JsonProperty("partName")
        private String partName;

        @JsonProperty("requiredCount")
        private int requiredCount;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class AuditionRow {
        @JsonProperty("id")
        private long id;

        @JsonProperty("songId")
        private long songId;

        @JsonProperty("performanceMemberId")
        private Long performanceMemberId;

        @JsonProperty("result")
        private String result;

        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Snapshot {
        @JsonProperty("songs")
        private List<SongRow> songs;

        @JsonProperty("players")
        private List<PlayerRow> players;

        @JsonProperty("teams")
        private List<TeamRow> teams;

        @JsonProperty("participants")
        private List<ParticipantRow> participants;

        @JsonProperty("requirements")
        private List<RequirementRow> requirements;

        @JsonProperty("auditions")
        private List<AuditionRow> auditions;

        @JsonProperty("maxSongsPerMember")
        private Integer maxSongsPerMember;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Evaluation {
        @JsonProperty("items")
        private List<LockChecklistItem> items;

        @JsonProperty("hardErrors")
        private List<LockChecklistItem> hardErrors;

        @JsonProperty("warnings")
        private List<LockChecklistItem> warnings;

        @JsonProperty("normalizedSetlist")
        private List<LockValidationResponse.NormalizedSong> normalizedSetlist;
    }

    /** 공연 확정 조건을 평가한다 */
    public static Evaluation evaluate(Snapshot snapshot) {
        // 기존 순서를 우선해 확정 곡을 정렬하고 1부터 연속된 정규화 순서를 만든다.
        List<SongRow> ordered = snapshot.getSongs().stream()
                .sorted(Comparator.comparing(SongRow::getOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparingLong(SongRow::getId))
                .toList();
        List<LockValidationResponse.NormalizedSong> normalized = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            normalized.add(new LockValidationResponse.NormalizedSong(
                    Long.toString(ordered.get(i).getId()), i + 1));
        }
        // 승인 연주자 데이터를 곡별·파트별·참여자별 집계 구조로 변환한다.
        Map<Long, Set<Long>> approvedPlayersBySong = new HashMap<>();
        Map<Long, Map<Long, Integer>> approvedPartsBySong = new HashMap<>();
        Map<Long, Set<Long>> confirmedSongsByPlayer = new HashMap<>();
        for (PlayerRow player : snapshot.getPlayers()) {
            if (!"APPROVED".equals(player.getMemberStatus())) continue;
            approvedPlayersBySong
                    .computeIfAbsent(player.getSongId(), ignored -> new HashSet<>())
                    .add(player.getPerformanceMemberId());
            approvedPartsBySong
                    .computeIfAbsent(player.getSongId(), ignored -> new HashMap<>())
                    .merge(player.getPartId(), 1, Integer::sum);
            confirmedSongsByPlayer
                    .computeIfAbsent(player.getPerformanceMemberId(), ignored -> new HashSet<>())
                    .add(player.getSongId());
        }
        Map<String, List<LockChecklistItem>> byCheck = new LinkedHashMap<>();
        for (int i = 1; i <= 11; i++) byCheck.put(checkId(i), new ArrayList<>());

        // L-01~L-03: 곡 순서·승인 팀·승인 연주자 필수 조건을 검사한다.
        for (SongRow song : ordered) {
            if (song.getOrder() == null)
                add(byCheck, 1, "SETLIST_ORDER_MISSING", "선정곡에 세트리스트 순서가 없습니다.", "SONG", song.getId());
            if (song.getAssignedTeamId() == null || !"APPROVED".equals(song.getAssignedTeamStatus())) {
                add(byCheck, 2, "TEAM_NOT_APPROVED", "선정곡의 담당 팀이 승인되지 않았습니다.", "SONG", song.getId());
            }
            if (approvedPlayersBySong.getOrDefault(song.getId(), Set.of()).isEmpty()) {
                add(byCheck, 3, "PLAYER_MISSING", "선정곡에 승인된 연주자가 없습니다.", "SONG", song.getId());
            }
        }
        // L-04~L-06: 대기 중인 팀·참여자·오디션을 확정 차단 오류로 수집한다.
        for (TeamRow team : snapshot.getTeams()) {
            if ("SUBMITTED".equals(team.getStatus()))
                add(byCheck, 4, "SUBMITTED_TEAM", "심사 대기 중인 팀이 있습니다.", "TEAM", team.getId());
        }
        for (ParticipantRow participant : snapshot.getParticipants()) {
            if ("APPLIED".equals(participant.getStatus()))
                add(
                        byCheck,
                        5,
                        "APPLIED_PARTICIPANT",
                        "승인 대기 중인 공연 참여자가 있습니다.",
                        "PERFORMANCE_MEMBER",
                        participant.getId());
        }
        for (AuditionRow audition : snapshot.getAuditions()) {
            if (audition.getResult() == null)
                add(byCheck, 6, "AUDITION_PENDING", "심사 대기 중인 오디션이 있습니다.", "AUDITION", audition.getId());
        }
        // L-07~L-09: 파트 부족과 참여곡 누락·권장 곡 수 초과를 경고한다.
        for (RequirementRow requirement : snapshot.getRequirements()) {
            int assigned = approvedPartsBySong
                    .getOrDefault(requirement.getSongId(), Map.of())
                    .getOrDefault(requirement.getPartId(), 0);
            int missing = requirement.getRequiredCount() - assigned;
            if (missing > 0)
                add(
                        byCheck,
                        7,
                        "PART_SHORTAGE",
                        requirement.getPartName() + "이 " + missing + "명 부족합니다.",
                        "SONG",
                        requirement.getSongId());
        }
        for (ParticipantRow participant : snapshot.getParticipants()) {
            if (!"APPROVED".equals(participant.getStatus())) continue;
            int count = confirmedSongsByPlayer
                    .getOrDefault(participant.getId(), Set.of())
                    .size();
            if (count == 0)
                add(
                        byCheck,
                        8,
                        "NO_SONG_PARTICIPATION",
                        "참여곡이 없는 승인 참여자입니다.",
                        "PERFORMANCE_MEMBER",
                        participant.getId());
            if (snapshot.getMaxSongsPerMember() != null && count > snapshot.getMaxSongsPerMember()) {
                add(byCheck, 9, "SONG_LIMIT_EXCEEDED", "권장 출연 곡 수를 초과했습니다.", "PERFORMANCE_MEMBER", participant.getId());
            }
        }
        // L-10: 인접 곡에 중복 배정된 연주자를 찾아 연속 출연을 경고한다.
        for (int i = 1; i < ordered.size(); i++) {
            SongRow previous = ordered.get(i - 1);
            SongRow current = ordered.get(i);
            if (previous.getOrder() == null || current.getOrder() == null) continue;
            Set<Long> repeated = new HashSet<>(approvedPlayersBySong.getOrDefault(previous.getId(), Set.of()));
            repeated.retainAll(approvedPlayersBySong.getOrDefault(current.getId(), Set.of()));
            for (Long playerId : repeated)
                add(
                        byCheck,
                        10,
                        "CONSECUTIVE_APPEARANCE",
                        "같은 연주자가 연속 출연합니다. 참여자 ID: " + playerId,
                        "SONG",
                        current.getId());
        }
        // L-11: 곡별 최신 전체 심사 결과가 탈락인지 확인한다.
        Map<Long, AuditionRow> latestSongAudition = new HashMap<>();
        for (AuditionRow audition : snapshot.getAuditions()) {
            if (audition.getPerformanceMemberId() == null) {
                latestSongAudition.merge(
                        audition.getSongId(),
                        audition,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt())
                                        || a.getCreatedAt().isEqual(b.getCreatedAt()) && a.getId() > b.getId()
                                ? a
                                : b);
            }
        }
        for (SongRow song : ordered) {
            AuditionRow latest = latestSongAudition.get(song.getId());
            if (latest != null && "FAILED".equals(latest.getResult()))
                add(byCheck, 11, "FAILED_SONG_AUDITION", "최신 곡 심사에서 탈락한 선정곡입니다.", "SONG", song.getId());
        }

        // 검사 번호 순으로 만족 항목을 채우고 1~6은 오류, 7~11은 경고로 분리한다.
        List<LockChecklistItem> items = new ArrayList<>();
        List<LockChecklistItem> hard = new ArrayList<>();
        List<LockChecklistItem> warnings = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            List<LockChecklistItem> failures = byCheck.get(checkId(i));
            if (failures.isEmpty()) {
                items.add(new LockChecklistItem(
                        checkId(i), LockChecklistItem.Result.SATISFIED, type(i), "조건을 충족했습니다.", null, null));
            } else {
                items.addAll(failures);
                (i <= 6 ? hard : warnings).addAll(failures);
            }
        }
        return new Evaluation(List.copyOf(items), List.copyOf(hard), List.copyOf(warnings), List.copyOf(normalized));
    }

    /** 잠금 점검 항목을 검사 번호별 결과 맵에 추가한다. */
    private static void add(
            Map<String, List<LockChecklistItem>> byCheck,
            int number,
            String type,
            String message,
            String targetType,
            long targetId) {
        byCheck.get(checkId(number))
                .add(new LockChecklistItem(
                        checkId(number),
                        number <= 6 ? LockChecklistItem.Result.UNMET : LockChecklistItem.Result.WARNING,
                        type,
                        message,
                        targetType,
                        Long.toString(targetId)));
    }

    /** 검사 번호를 잠금 체크 식별자 형식으로 변환한다. */
    private static String checkId(int number) {
        return "L-%02d".formatted(number);
    }

    /** 검사 번호에 대응하는 오류 유형 문자열을 반환한다. */
    private static String type(int number) {
        return switch (number) {
            case 1 -> "SETLIST_ORDER_MISSING";
            case 2 -> "TEAM_NOT_APPROVED";
            case 3 -> "PLAYER_MISSING";
            case 4 -> "SUBMITTED_TEAM";
            case 5 -> "APPLIED_PARTICIPANT";
            case 6 -> "AUDITION_PENDING";
            case 7 -> "PART_SHORTAGE";
            case 8 -> "NO_SONG_PARTICIPATION";
            case 9 -> "SONG_LIMIT_EXCEEDED";
            case 10 -> "CONSECUTIVE_APPEARANCE";
            case 11 -> "FAILED_SONG_AUDITION";
            default -> throw new IllegalArgumentException("알 수 없는 점검 항목입니다.");
        };
    }
}
