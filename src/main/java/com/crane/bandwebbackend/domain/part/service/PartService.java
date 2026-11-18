package com.crane.bandwebbackend.domain.part.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.CoreEntityLookupService;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.entity.MembershipPart;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.repository.MembershipPartRepository;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.RevisionTag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartService {
    private static final List<String> DEFAULT_NAMES = List.of("보컬", "기타", "베이스", "키보드", "드럼", "그 외");

    private final PartRepository repository;
    private final MembershipService membershipService;
    private final ParticipationLookup participationLookup;
    private final EntityManager entityManager;
    private final MembershipPartRepository membershipPartRepository;
    private final CoreEntityLookupService entityLookup;
    private final AuditRecorder auditRecorder;

    /** PART-01. 객원은 참여 공연이 있을 때 활성 파트와 본인의 과거 참조 파트만 본다. */
    @Transactional(readOnly = true)
    public List<Part> list(Long clubId, Long actorUserId, boolean includeInactive) {
        // 요청자의 소속 유형에 따라 전체 목록 또는 객원 제한 목록을 결정한다.
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        List<Part> parts = repository.findByClub_IdOrderByDisplayOrderAscIdAsc(clubId);
        // 정회원은 요청 옵션에 따라 비활성 파트 포함 여부만 적용한다.
        if (actor.getMemberType() != MemberType.GUEST) {
            return includeInactive
                    ? parts
                    : parts.stream().filter(Part::isActive).toList();
        }
        if (participationLookup.visiblePerformanceIds(actor.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파트 목록을 찾을 수 없습니다.");
        }
        if (!includeInactive) {
            return parts.stream().filter(Part::isActive).toList();
        }
        // 객원은 자신의 과거 연주 기록에서 참조한 비활성 파트만 추가로 볼 수 있다.
        List<Long> ownPartIds = repository.findHistoricallyReferencedPartIds(actor.getId());
        return parts.stream()
                .filter(part -> part.isActive() || ownPartIds.contains(part.getId()))
                .toList();
    }

    /** PART-02. 동아리 행을 먼저 잠가 동시 생성/수정의 정렬·중복 검사를 직렬화한다. */
    @Transactional
    public Part create(Long clubId, Long actorUserId, String name, Integer displayOrder) {
        // 관리자 권한을 확인하고 동아리 행을 잠가 이름 중복 검사를 직렬화한다.
        Membership actor = membershipService.requireAdmin(clubId, actorUserId);
        entityManager.lock(actor.getClub(), LockModeType.PESSIMISTIC_WRITE);
        String normalized = validateName(name);
        if (repository.existsByClub_IdAndName(clubId, normalized)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
        // 애플리케이션 검사 이후의 동시 삽입 충돌도 DB 예외를 동일한 중복 오류로 변환한다.
        try {
            Part part = repository.saveAndFlush(
                    Part.create(actor.getClub(), normalized, displayOrder == null ? 0 : displayOrder));
            entityManager.refresh(part);
            return part;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
    }

    // 동아리 생성(API-011) 전용: 권한 검사 없이 기본 파트 6개를 만든다. 다른 곳에서 호출하지 않는다
    @Transactional
    public void createDefaults(Club club) {
        List<Part> parts = new ArrayList<>();
        for (int order = 0; order < DEFAULT_NAMES.size(); order++) {
            parts.add(Part.create(club, DEFAULT_NAMES.get(order), order));
        }
        repository.saveAll(parts);
    }

    /** PART-03. 예상 revision은 파트 잠금 후 다시 계산한다. */
    @Transactional
    public Part update(
            Long clubId, Long partId, Long actorUserId, String expectedRevision, String name, Integer displayOrder) {
        // 관리자·동아리·파트 순서로 잠가 동시 수정의 교착 가능성을 줄인다.
        Membership actor = membershipService.requireAdmin(clubId, actorUserId);
        entityManager.lock(actor.getClub(), LockModeType.PESSIMISTIC_WRITE);
        Part part = requireLocked(clubId, partId, expectedRevision);
        if (name == null && displayOrder == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "변경할 정보가 없습니다.");
        }
        // 이름이 전달된 경우에만 정규화와 동아리 내 중복 검사를 수행한다.
        if (name != null) {
            String normalized = validateName(name);
            if (!normalized.equals(part.getName()) && repository.existsByClub_IdAndName(clubId, normalized)) {
                throw new BusinessException(ErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
            }
            name = normalized;
        }
        // 엔티티에 변경을 적용하고 DB 유일 제약까지 즉시 확인한다.
        part.changeDetails(name, displayOrder);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
        entityManager.refresh(part);
        return part;
    }

    /** PART-04. 동일 상태 요청은 무변경이며 과거 연결은 삭제하지 않는다. */
    @Transactional
    public Part setActive(Long clubId, Long partId, Long actorUserId, String expectedRevision, boolean active) {
        // 권한과 revision을 잠금 상태에서 확인한다.
        Membership actor = membershipService.requireAdmin(clubId, actorUserId);
        entityManager.lock(actor.getClub(), LockModeType.PESSIMISTIC_WRITE);
        Part part = requireLocked(clubId, partId, expectedRevision);
        // 동일 상태 요청은 멱등하게 아무 변경 없이 현재 파트를 반환한다.
        if (part.isActive() != active) {
            part.changeActive(active);
            repository.flush();
        }
        entityManager.refresh(part);
        return part;
    }

    /** API-032. 동아리 행을 먼저 잠가 같은 소속의 동시 저장을 직렬화한다. 임원은 모든 소속, 일반 부원은 본인 소속만 저장한다 */
    @Transactional
    public Membership replaceMemberParts(Long clubId, Long membershipId, Long actorUserId, List<String> partIds) {
        entityLookup.lockActiveClub(clubId);
        Membership target = membershipService.require(clubId, membershipId);
        if (!target.getUser().getId().equals(actorUserId)) {
            membershipService.requireAdmin(clubId, actorUserId);
        }
        if (target.getStatus() != MembershipStatus.ACTIVE
                && target.getStatus() != MembershipStatus.ON_LEAVE
                && target.getStatus() != MembershipStatus.GRADUATED) {
            throw new BusinessException(
                    ErrorCode.STATE_CONFLICT, "INVALID_MEMBERSHIP_STATUS", "파트를 저장할 수 없는 소속 상태입니다.");
        }
        List<Long> ids = parsePartIds(partIds);
        Set<Long> held = membershipPartRepository.findByMembership_Id(membershipId).stream()
                .map(row -> row.getPart().getId())
                .collect(Collectors.toSet());
        List<Part> parts = new ArrayList<>();
        for (Long id : ids) {
            Part part = repository
                    .findByIdAndClub_Id(id, clubId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다."));
            // 이미 가진 비활성 파트는 유지하고, 새로 추가하는 비활성 파트만 막는다
            if (!part.isActive() && !held.contains(id)) {
                throw new BusinessException(ErrorCode.RULE_VIOLATION, "PART_INACTIVE", "사용 중지 파트는 추가할 수 없습니다.");
            }
            parts.add(part);
        }
        // 본인·임원 저장 모두 감사 기록을 남기되, 바뀐 것이 없으면 남기지 않는다(API-019와 같은 기준)
        if (!held.equals(new HashSet<>(ids))) {
            auditRecorder.record(
                    clubId,
                    actorUserId,
                    "MEMBERSHIP_PARTS_CHANGED",
                    "MEMBERSHIP",
                    membershipId,
                    null,
                    Map.of("from", sortedIds(held), "to", sortedIds(ids)));
        }
        // 삭제를 먼저 반영해야 같은 (소속, 파트) 키를 다시 넣을 때 충돌하지 않는다
        membershipPartRepository.deleteByMembership_Id(membershipId);
        membershipPartRepository.flush();
        // API-142 ADD는 동아리 잠금 없이 넣으므로 같은 (소속, 파트)가 먼저 들어오면 키 충돌이 난다.
        // 500 대신 409 VERSION_CONFLICT로 돌려 FE가 다시 불러온 뒤 재시도하게 한다
        try {
            membershipPartRepository.saveAll(
                    parts.stream().map(part -> MembershipPart.of(target, part)).toList());
            membershipPartRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "보유 파트가 먼저 변경되었습니다.");
        }
        return target;
    }

    // 감사 detail은 ID 문자열을 오름차순으로 담는다(U-09)
    private static List<String> sortedIds(Collection<Long> ids) {
        return ids.stream().sorted().map(String::valueOf).toList();
    }

    /** partIds는 필수이며 양수 ID 형식·중복 없음을 요구한다. 오류는 field partIds로 돌려준다 */
    private static List<Long> parsePartIds(List<String> partIds) {
        if (partIds == null) {
            throw invalidPartIds("partIds가 필요합니다.");
        }
        List<Long> ids = new ArrayList<>();
        for (String raw : partIds) {
            try {
                ids.add(ApiIds.positive(raw, "partIds"));
            } catch (BusinessException exception) {
                throw invalidPartIds("partIds 형식이 유효하지 않습니다.");
            }
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalidPartIds("partIds가 중복됩니다.");
        }
        return ids;
    }

    private static BusinessException invalidPartIds(String message) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, message, List.of(ErrorDetail.of("partIds", "INVALID_VALUE")));
    }

    /** 파트 행을 잠그고 요청 버전과 현재 버전의 일치를 확인한다. */
    private Part requireLocked(Long clubId, Long partId, String expectedRevision) {
        Part part = repository
                .findWithLockByIdAndClub_Id(partId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다."));
        if (expectedRevision == null
                || !RevisionTag.from("part", part.getId(), part.getUpdatedAt()).equals(expectedRevision)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "파트가 먼저 변경되었습니다.");
        }
        return part;
    }

    /** 파트 이름을 정규화하고 필수값 및 길이를 검증한다. */
    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파트 이름은 1~50자여야 합니다.");
        }
        return name.trim();
    }
}
