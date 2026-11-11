package com.crane.bandwebbackend.domain.schedule.controller;

import com.crane.bandwebbackend.domain.schedule.dto.ScheduleConfirmRequest;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleCreateRequest;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleDetailResponse;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleMutationResponse;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleResponse;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleVersionRequest;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.service.PracticeScheduleService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleCreateService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleDetailReader;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleEditService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleTargetReader;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleTransitionService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleValidationService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCH-01. 객원 일정은 현재 승인 참여자와 일정의 팀·곡 대상 연결을 함께 확인한다.
 * 참석 대상은 팀원/연주자 연결과 함께 계산해야 한다.
 * 인증 필터 연결 전에는 이 라우트를 등록하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/schedules")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PracticeScheduleController {
    private final PracticeScheduleService service;
    private final ScheduleTargetReader targetReader;
    private final ScheduleTransitionService transitionService;
    private final ScheduleEditService editService;
    private final ScheduleDetailReader detailReader;
    private final ScheduleCreateService createService;
    private final ScheduleValidationService validationService;
    private final CurrentActor currentActor;

    /** 일정 목록을 조회한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ScheduleResponse>>> list(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startAt,asc") String sort,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(name = "eventType", required = false) ScheduleType eventType,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String songId,
            @RequestParam(defaultValue = "false") boolean mine,
            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 또는 size 범위가 유효하지 않습니다.");
        }
        if (!sort.equals("startAt,asc")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sort 값이 유효하지 않습니다.");
        }
        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.ASC, "startAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<PracticeSchedule> schedules = service.page(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                status,
                eventType,
                teamId == null ? null : ApiIds.positive(teamId, "teamId"),
                songId == null ? null : ApiIds.positive(songId, "songId"),
                mine,
                pageable);
        List<Long> ids =
                schedules.getContent().stream().map(PracticeSchedule::getId).toList();
        Map<Long, List<String>> teams = targetReader.teamIds(ids);
        Map<Long, List<String>> songs = targetReader.songIds(ids);
        Page<ScheduleResponse> response = schedules.map(schedule -> ScheduleResponse.from(
                schedule,
                teams.getOrDefault(schedule.getId(), List.of()),
                songs.getOrDefault(schedule.getId(), List.of())));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(response)));
    }

    /** 일정 생성한다 */
    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleMutationResponse>> create(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody ScheduleCreateRequest body,
            HttpServletRequest request) {
        var result = createService.create(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ScheduleMutationResponse(
                        response(result.schedule()), result.warnings(), ScheduleMutationResponse.Effects.none())));
    }

    /** 일정 생성 요청을 검증한다 */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCreate(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody ScheduleCreateRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(validationService.create(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 일정 상세 정보를 조회한다 */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleDetailResponse>> detail(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            HttpServletRequest request) {
        Long id = ApiIds.positive(scheduleId, "scheduleId");
        PracticeSchedule schedule = service.detail(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                id,
                currentActor.requireUserId());
        return ResponseEntity.ok(
                ApiResponse.ok(new ScheduleDetailResponse(response(schedule), detailReader.participants(id))));
    }

    /** API-113: 임원은 대상 전체, 일반 부원은 자신에게 해당하는 충돌만 본다. */
    @GetMapping("/{scheduleId}/conflicts")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> warnings(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            HttpServletRequest request) {
        List<Map<String, Object>> items = editService.warningsFor(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("items", items)));
    }

    /** API-110: 수정은 즉시 저장하고 겹침·시즌 경고를 결과에 담는다. */
    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleMutationResponse>> edit(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        ScheduleEditService.EditResult result = editService.edit(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId(),
                body);
        return ResponseEntity.ok(ApiResponse.ok(new ScheduleMutationResponse(
                response(result.schedule()), result.warnings(), ScheduleMutationResponse.Effects.none())));
    }

    /** 일정 수정 요청을 검증한다 */
    @PostMapping("/{scheduleId}/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateEdit(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(validationService.edit(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 일정 취소한다 */
    @PostMapping("/{scheduleId}/cancel")
    public ResponseEntity<ApiResponse<ScheduleMutationResponse>> cancel(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleVersionRequest body,
            HttpServletRequest request) {
        PracticeSchedule schedule = transitionService.change(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId(),
                body.version(),
                ScheduleStatus.CANCELED,
                body.reason());
        return ResponseEntity.ok(ApiResponse.ok(ScheduleMutationResponse.of(response(schedule))));
    }

    /** 일정 확정한다 */
    @PostMapping("/{scheduleId}/confirm")
    public ResponseEntity<ApiResponse<ScheduleMutationResponse>> confirm(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleConfirmRequest body,
            HttpServletRequest request) {
        ScheduleTransitionService.ConfirmResult result = transitionService.confirm(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId(),
                body.version(),
                Boolean.TRUE.equals(body.confirmed()));
        return ResponseEntity.ok(ApiResponse.ok(new ScheduleMutationResponse(
                response(result.schedule()), result.warnings(), ScheduleMutationResponse.Effects.none())));
    }

    /** 일정 확정을 해제한다 */
    @PostMapping("/{scheduleId}/unconfirm")
    public ResponseEntity<ApiResponse<ScheduleResponse>> unconfirm(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleVersionRequest body,
            HttpServletRequest request) {
        if (body.reason() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unconfirm은 reason을 받지 않습니다.");
        }
        PracticeSchedule schedule = transitionService.change(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(scheduleId, "scheduleId"),
                currentActor.requireUserId(),
                body.version(),
                ScheduleStatus.TENTATIVE,
                null);
        return ResponseEntity.ok(ApiResponse.ok(response(schedule)));
    }

    /** 일정 엔티티와 연결 대상을 상세 응답으로 조합한다. */
    private ScheduleResponse response(PracticeSchedule schedule) {
        Long id = schedule.getId();
        return ScheduleResponse.from(
                schedule,
                targetReader.teamIds(List.of(id)).getOrDefault(id, List.of()),
                targetReader.songIds(List.of(id)).getOrDefault(id, List.of()));
    }
}
