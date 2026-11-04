package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.performance.dto.LockChecklistItem;
import com.crane.bandwebbackend.domain.performance.dto.LockValidationResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** L-01~L-11의 판정은 DB 접근 없이 수행해 검사·확정·홈 점검이 동일한 결과를 쓴다. */
public final class LockChecklistEvaluator {
	/** 정적 평가기이므로 인스턴스 생성을 막는다. */
	private LockChecklistEvaluator() { }

	public record SongRow(long id, Integer order, Long assignedTeamId, String assignedTeamStatus) { }
	public record PlayerRow(long songId, long performanceMemberId, long partId, String memberStatus) { }
	public record TeamRow(long id, String status) { }
	public record ParticipantRow(long id, String status) { }
	public record RequirementRow(long songId, long partId, String partName, int requiredCount) { }
	public record AuditionRow(long id, long songId, Long performanceMemberId,
							  String result, LocalDateTime createdAt) { }
	public record Snapshot(List<SongRow> songs, List<PlayerRow> players, List<TeamRow> teams,
						   List<ParticipantRow> participants, List<RequirementRow> requirements,
						   List<AuditionRow> auditions, Integer maxSongsPerMember) { }
	public record Evaluation(List<LockChecklistItem> items, List<LockChecklistItem> hardErrors,
							 List<LockChecklistItem> warnings,
							 List<LockValidationResponse.NormalizedSong> normalizedSetlist) { }

	/** 공연 확정 조건을 평가한다 */
	public static Evaluation evaluate(Snapshot snapshot) {
		// 기존 순서를 우선해 확정 곡을 정렬하고 1부터 연속된 정규화 순서를 만든다.
		List<SongRow> ordered = snapshot.songs().stream()
				.sorted(Comparator.comparing(SongRow::order, Comparator.nullsLast(Integer::compareTo))
						.thenComparingLong(SongRow::id)).toList();
		List<LockValidationResponse.NormalizedSong> normalized = new ArrayList<>();
		for (int i = 0; i < ordered.size(); i++) {
			normalized.add(new LockValidationResponse.NormalizedSong(Long.toString(ordered.get(i).id()), i + 1));
		}
		// 승인 연주자 데이터를 곡별·파트별·참여자별 집계 구조로 변환한다.
		Map<Long, Set<Long>> approvedPlayersBySong = new HashMap<>();
		Map<Long, Map<Long, Integer>> approvedPartsBySong = new HashMap<>();
		Map<Long, Set<Long>> confirmedSongsByPlayer = new HashMap<>();
		for (PlayerRow player : snapshot.players()) {
			if (!"APPROVED".equals(player.memberStatus())) continue;
			approvedPlayersBySong.computeIfAbsent(player.songId(), ignored -> new HashSet<>())
					.add(player.performanceMemberId());
			approvedPartsBySong.computeIfAbsent(player.songId(), ignored -> new HashMap<>())
					.merge(player.partId(), 1, Integer::sum);
			confirmedSongsByPlayer.computeIfAbsent(player.performanceMemberId(), ignored -> new HashSet<>())
					.add(player.songId());
		}
		Map<String, List<LockChecklistItem>> byCheck = new LinkedHashMap<>();
		for (int i = 1; i <= 11; i++) byCheck.put(checkId(i), new ArrayList<>());

		// L-01~L-03: 곡 순서·승인 팀·승인 연주자 필수 조건을 검사한다.
		for (SongRow song : ordered) {
			if (song.order() == null) add(byCheck, 1, "SETLIST_ORDER_MISSING",
					"선정곡에 세트리스트 순서가 없습니다.", "SONG", song.id());
			if (song.assignedTeamId() == null || !"APPROVED".equals(song.assignedTeamStatus())) {
				add(byCheck, 2, "TEAM_NOT_APPROVED", "선정곡의 담당 팀이 승인되지 않았습니다.", "SONG", song.id());
			}
			if (approvedPlayersBySong.getOrDefault(song.id(), Set.of()).isEmpty()) {
				add(byCheck, 3, "PLAYER_MISSING", "선정곡에 승인된 연주자가 없습니다.", "SONG", song.id());
			}
		}
		// L-04~L-06: 대기 중인 팀·참여자·오디션을 확정 차단 오류로 수집한다.
		for (TeamRow team : snapshot.teams()) {
			if ("SUBMITTED".equals(team.status())) add(byCheck, 4, "SUBMITTED_TEAM",
					"심사 대기 중인 팀이 있습니다.", "TEAM", team.id());
		}
		for (ParticipantRow participant : snapshot.participants()) {
			if ("APPLIED".equals(participant.status())) add(byCheck, 5, "APPLIED_PARTICIPANT",
					"승인 대기 중인 공연 참여자가 있습니다.", "PERFORMANCE_MEMBER", participant.id());
		}
		for (AuditionRow audition : snapshot.auditions()) {
			if (audition.result() == null) add(byCheck, 6, "AUDITION_PENDING",
					"심사 대기 중인 오디션이 있습니다.", "AUDITION", audition.id());
		}
		// L-07~L-09: 파트 부족과 참여곡 누락·권장 곡 수 초과를 경고한다.
		for (RequirementRow requirement : snapshot.requirements()) {
			int assigned = approvedPartsBySong.getOrDefault(requirement.songId(), Map.of())
					.getOrDefault(requirement.partId(), 0);
			int missing = requirement.requiredCount() - assigned;
			if (missing > 0) add(byCheck, 7, "PART_SHORTAGE",
					requirement.partName() + "이 " + missing + "명 부족합니다.", "SONG", requirement.songId());
		}
		for (ParticipantRow participant : snapshot.participants()) {
			if (!"APPROVED".equals(participant.status())) continue;
			int count = confirmedSongsByPlayer.getOrDefault(participant.id(), Set.of()).size();
			if (count == 0) add(byCheck, 8, "NO_SONG_PARTICIPATION",
					"참여곡이 없는 승인 참여자입니다.", "PERFORMANCE_MEMBER", participant.id());
			if (snapshot.maxSongsPerMember() != null && count > snapshot.maxSongsPerMember()) {
				add(byCheck, 9, "SONG_LIMIT_EXCEEDED", "권장 출연 곡 수를 초과했습니다.",
						"PERFORMANCE_MEMBER", participant.id());
			}
		}
		// L-10: 인접 곡에 중복 배정된 연주자를 찾아 연속 출연을 경고한다.
		for (int i = 1; i < ordered.size(); i++) {
			SongRow previous = ordered.get(i - 1);
			SongRow current = ordered.get(i);
			if (previous.order() == null || current.order() == null) continue;
			Set<Long> repeated = new HashSet<>(approvedPlayersBySong.getOrDefault(previous.id(), Set.of()));
			repeated.retainAll(approvedPlayersBySong.getOrDefault(current.id(), Set.of()));
			for (Long playerId : repeated) add(byCheck, 10, "CONSECUTIVE_APPEARANCE",
					"같은 연주자가 연속 출연합니다. 참여자 ID: " + playerId,
					"SONG", current.id());
		}
		// L-11: 곡별 최신 전체 심사 결과가 탈락인지 확인한다.
		Map<Long, AuditionRow> latestSongAudition = new HashMap<>();
		for (AuditionRow audition : snapshot.auditions()) {
			if (audition.performanceMemberId() == null) {
				latestSongAudition.merge(audition.songId(), audition,
						(a, b) -> a.createdAt().isAfter(b.createdAt())
								|| a.createdAt().isEqual(b.createdAt()) && a.id() > b.id() ? a : b);
			}
		}
		for (SongRow song : ordered) {
			AuditionRow latest = latestSongAudition.get(song.id());
			if (latest != null && "FAILED".equals(latest.result())) add(byCheck, 11, "FAILED_SONG_AUDITION",
					"최신 곡 심사에서 탈락한 선정곡입니다.", "SONG", song.id());
		}

		// 검사 번호 순으로 만족 항목을 채우고 1~6은 오류, 7~11은 경고로 분리한다.
		List<LockChecklistItem> items = new ArrayList<>();
		List<LockChecklistItem> hard = new ArrayList<>();
		List<LockChecklistItem> warnings = new ArrayList<>();
		for (int i = 1; i <= 11; i++) {
			List<LockChecklistItem> failures = byCheck.get(checkId(i));
			if (failures.isEmpty()) {
				items.add(new LockChecklistItem(checkId(i), LockChecklistItem.Result.SATISFIED,
						type(i), "조건을 충족했습니다.", null, null));
			} else {
				items.addAll(failures);
				(i <= 6 ? hard : warnings).addAll(failures);
			}
		}
		return new Evaluation(List.copyOf(items), List.copyOf(hard), List.copyOf(warnings),
				List.copyOf(normalized));
	}

	/** 잠금 점검 항목을 검사 번호별 결과 맵에 추가한다. */
	private static void add(Map<String, List<LockChecklistItem>> byCheck, int number, String type,
							String message, String targetType, long targetId) {
		byCheck.get(checkId(number)).add(new LockChecklistItem(checkId(number),
				number <= 6 ? LockChecklistItem.Result.UNMET : LockChecklistItem.Result.WARNING,
				type, message, targetType, Long.toString(targetId)));
	}

	/** 검사 번호를 잠금 체크 식별자 형식으로 변환한다. */
	private static String checkId(int number) { return "L-%02d".formatted(number); }

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
