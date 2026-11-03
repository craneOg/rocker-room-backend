package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.entity.MembershipPart;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * song·team·setlist·availability·playability·vote가 공유하는 공연 접근·조회.
 * ponytail: membership·participant·part 도메인 service가 생기면 그쪽 호출로 교체(지금은 JPQL 직접 조회)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceAccessService {

    private static final List<MembershipStatus> ACCESSIBLE =
            List.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE, MembershipStatus.GRADUATED);

    private final EntityManager em;

    public PerformanceAccess get(PerformanceRef ref) {
        return get(ref.getClubId(), ref.getPerformanceId(), ref.getUserId());
    }

    public PerformanceAccess getForUpdate(PerformanceRef ref) {
        return getForUpdate(ref.getClubId(), ref.getPerformanceId(), ref.getUserId());
    }

    public PerformanceAccess get(Long clubId, Long performanceId, Long userId) {
        return load(clubId, em.find(Performance.class, performanceId), userId);
    }

    // 여러 행에 걸친 조건(한도, 순서)은 공연 행을 잠근 뒤 한 트랜잭션에서 검사(0.4)
    public PerformanceAccess getForUpdate(Long clubId, Long performanceId, Long userId) {
        return load(clubId, em.find(Performance.class, performanceId, LockModeType.PESSIMISTIC_WRITE), userId);
    }

    private PerformanceAccess load(Long clubId, Performance performance, Long userId) {
        // 다른 동아리 공연은 존재를 숨김(G-04)
        if (performance == null || !performance.getClub().getId().equals(clubId)) {
            throw notFound();
        }
        // 소속·참여자를 한 번에(모든 요청이 지나가는 경로)
        Object[] row = em.createQuery(
                        "select m, pm from Membership m join fetch m.club join fetch m.user"
                                + " left join PerformanceMember pm on pm.membership = m and pm.performance.id = :pid"
                                + " where m.club.id = :clubId and m.user.id = :userId and m.status in :statuses",
                        Object[].class)
                .setParameter("pid", performance.getId())
                .setParameter("clubId", clubId)
                .setParameter("userId", userId)
                .setParameter("statuses", ACCESSIBLE)
                .getResultStream()
                .findFirst()
                .orElseThrow(PerformanceAccessService::notFound);
        Membership membership = (Membership) row[0];
        PerformanceMember pm = (PerformanceMember) row[1];
        // 외부 객원은 자신이 참여한 공연만 열람
        if (membership.getMemberType() == MemberType.GUEST && pm == null) {
            throw notFound();
        }
        return new PerformanceAccess(performance, membership, pm);
    }

    public PerformancePolicy getPolicy(Long performanceId) {
        PerformancePolicy policy = em.find(PerformancePolicy.class, performanceId);
        if (policy == null) {
            throw notFound();
        }
        return policy;
    }

    public PerformanceMember getMember(Long performanceId, Long performanceMemberId) {
        PerformanceMember pm = em.find(PerformanceMember.class, performanceMemberId);
        if (pm == null || !pm.getPerformance().getId().equals(performanceId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공연 참여자를 찾을 수 없습니다.");
        }
        return pm;
    }

    // 1인 한도(V-05) 검사 전 참여자 행 잠금. 가능 시간 저장도 같은 잠금(API-101)
    public void lock(PerformanceMember pm) {
        em.lock(pm, LockModeType.PESSIMISTIC_WRITE);
    }

    // 여러 명을 한 쿼리로. 하나라도 이 공연 참여자가 아니면 404
    public Map<Long, PerformanceMember> getMembers(Long performanceId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PerformanceMember> found = em.createQuery(
                        "select pm from PerformanceMember pm join fetch pm.membership m join fetch m.user"
                                + " where pm.performance.id = :pid and pm.id in :ids",
                        PerformanceMember.class)
                .setParameter("pid", performanceId)
                .setParameter("ids", ids)
                .getResultStream()
                .collect(Collectors.toMap(PerformanceMember::getId, Function.identity()));
        if (found.size() != Set.copyOf(ids).size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공연 참여자를 찾을 수 없습니다.");
        }
        return found;
    }

    // 소속·사용자까지 한 번에 가져옴(N+1 방지)
    public List<PerformanceMember> getMembers(Long performanceId, List<PerformanceMemberStatus> statuses) {
        return em.createQuery(
                        "select pm from PerformanceMember pm join fetch pm.membership m join fetch m.user"
                                + " where pm.performance.id = :pid and pm.status in :statuses order by pm.id",
                        PerformanceMember.class)
                .setParameter("pid", performanceId)
                .setParameter("statuses", statuses)
                .getResultList();
    }

    public Optional<PerformanceMember> findMemberByMembership(Long performanceId, Long membershipId) {
        return em.createQuery(
                        "select pm from PerformanceMember pm where pm.performance.id = :pid"
                                + " and pm.membership.id = :mid",
                        PerformanceMember.class)
                .setParameter("pid", performanceId)
                .setParameter("mid", membershipId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public PerformanceMember addMember(
            Performance performance,
            Membership membership,
            PerformanceMemberStatus status,
            PerformanceMemberSource source) {
        PerformanceMember pm = PerformanceMember.create(performance, membership, status, source);
        em.persist(pm);
        return pm;
    }

    // 같은 동아리 소속만. 다른 동아리면 존재를 숨김(G-04)
    public Membership getMembership(Long clubId, Long membershipId) {
        Membership m = em.find(Membership.class, membershipId);
        if (m == null || !m.getClub().getId().equals(clubId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "부원을 찾을 수 없습니다.");
        }
        return m;
    }

    // 소속 ID → 등록 파트 ID(T-11 경고용)
    public Map<Long, Set<Long>> registeredPartIds(Collection<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("select mp from MembershipPart mp where mp.membership.id in :ids", MembershipPart.class)
                .setParameter("ids", membershipIds)
                .getResultStream()
                .collect(Collectors.groupingBy(
                        mp -> mp.getMembership().getId(),
                        Collectors.mapping(mp -> mp.getPart().getId(), Collectors.toSet())));
    }

    public Part getActivePart(Long clubId, Long partId) {
        return getActiveParts(clubId, List.of(partId)).get(partId);
    }

    // 같은 동아리 파트인지는 서비스 검증(DB 미강제). 사용 중지 파트는 새로 선택 불가(M-02)
    public Map<Long, Part> getActiveParts(Long clubId, Collection<Long> ids) {
        Map<Long, Part> found = getParts(clubId, ids);
        if (found.values().stream().anyMatch(p -> !p.isActive())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "PART_INACTIVE", "사용 중지된 파트입니다.");
        }
        return found;
    }

    public Map<Long, Part> getParts(Long clubId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Part> found = em.createQuery(
                        "select p from Part p where p.club.id = :clubId and p.id in :ids", Part.class)
                .setParameter("clubId", clubId)
                .setParameter("ids", ids)
                .getResultStream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        if (found.size() != Set.copyOf(ids).size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다.");
        }
        return found;
    }

    public static String displayName(PerformanceMember pm) {
        return displayName(pm.getMembership());
    }

    public static String displayName(Membership m) {
        return m.getDisplayName() != null ? m.getDisplayName() : m.getUser().getName();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다.");
    }
}
