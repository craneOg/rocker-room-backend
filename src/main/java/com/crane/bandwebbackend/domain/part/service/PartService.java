package com.crane.bandwebbackend.domain.part.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.RevisionTag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartService {
    private final PartRepository repository;
    private final MembershipService membershipService;
    private final ParticipationLookup participationLookup;
    private final EntityManager entityManager;

    /** PART-01. 객원은 참여 공연이 있을 때 활성 파트와 본인의 과거 참조 파트만 본다. */
    @Transactional(readOnly = true)
    public List<Part> list(Long clubId, Long actorUserId, boolean includeInactive) {
        // 요청자의 소속 유형에 따라 전체 목록 또는 객원 제한 목록을 결정한다.
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        List<Part> parts = repository.findByClub_IdOrderByDisplayOrderAscIdAsc(clubId);
        // 정회원은 요청 옵션에 따라 비활성 파트 포함 여부만 적용한다.
        if (actor.getMemberType() != MemberType.GUEST) {
            return includeInactive ? parts : parts.stream().filter(Part::isActive).toList();
        }
        if (participationLookup.visiblePerformanceIds(actor.getId()).isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "파트 목록을 찾을 수 없습니다.");
        }
        if (!includeInactive) {
            return parts.stream().filter(Part::isActive).toList();
        }
        // 객원은 자신의 과거 연주 기록에서 참조한 비활성 파트만 추가로 볼 수 있다.
        List<Long> ownPartIds = repository.findHistoricallyReferencedPartIds(actor.getId());
        return parts.stream().filter(part -> part.isActive() || ownPartIds.contains(part.getId())).toList();
    }

    /** PART-02. 동아리 행을 먼저 잠가 동시 생성/수정의 정렬·중복 검사를 직렬화한다. */
    @Transactional
    public Part create(Long clubId, Long actorUserId, String name, Integer displayOrder) {
        // 관리자 권한을 확인하고 동아리 행을 잠가 이름 중복 검사를 직렬화한다.
        Membership actor = membershipService.requireAdmin(clubId, actorUserId);
        entityManager.lock(actor.getClub(), LockModeType.PESSIMISTIC_WRITE);
        String normalized = validateName(name);
        if (repository.existsByClub_IdAndName(clubId, normalized)) {
            throw new ApiException(ApiErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
        // 애플리케이션 검사 이후의 동시 삽입 충돌도 DB 예외를 동일한 중복 오류로 변환한다.
        try {
            return repository.saveAndFlush(Part.create(actor.getClub(), normalized,
                    displayOrder == null ? 0 : displayOrder));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ApiErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
    }

    /** PART-03. 예상 revision은 파트 잠금 후 다시 계산한다. */
    @Transactional
    public Part update(Long clubId, Long partId, Long actorUserId, String expectedRevision,
                       String name, Integer displayOrder) {
        // 관리자·동아리·파트 순서로 잠가 동시 수정의 교착 가능성을 줄인다.
        Membership actor = membershipService.requireAdmin(clubId, actorUserId);
        entityManager.lock(actor.getClub(), LockModeType.PESSIMISTIC_WRITE);
        Part part = requireLocked(clubId, partId, expectedRevision);
        if (name == null && displayOrder == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "변경할 정보가 없습니다.");
        }
        // 이름이 전달된 경우에만 정규화와 동아리 내 중복 검사를 수행한다.
        if (name != null) {
            String normalized = validateName(name);
            if (!normalized.equals(part.getName()) && repository.existsByClub_IdAndName(clubId, normalized)) {
                throw new ApiException(ApiErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
            }
            name = normalized;
        }
        // 엔티티에 변경을 적용하고 DB 유일 제약까지 즉시 확인한다.
        part.changeDetails(name, displayOrder);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ApiErrorCode.DUPLICATE, "같은 이름의 파트가 있습니다.");
        }
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
        return part;
    }

    /** 파트 행을 잠그고 요청 버전과 현재 버전의 일치를 확인한다. */
    private Part requireLocked(Long clubId, Long partId, String expectedRevision) {
        Part part = repository.findWithLockByIdAndClub_Id(partId, clubId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다."));
        if (expectedRevision == null || !RevisionTag.from("part", part.getId(), part.getUpdatedAt())
                .equals(expectedRevision)) {
            throw new ApiException(ApiErrorCode.VERSION_CONFLICT, "파트가 먼저 변경되었습니다.");
        }
        return part;
    }

    /** 파트 이름을 정규화하고 필수값 및 길이를 검증한다. */
    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 50) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "파트 이름은 1~50자여야 합니다.");
        }
        return name.trim();
    }
}
