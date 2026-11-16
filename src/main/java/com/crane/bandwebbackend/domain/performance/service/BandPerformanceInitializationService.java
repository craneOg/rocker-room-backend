package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.team.entity.TeamMember;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BAND 공연의 기본 참여자와 단일 기본 팀을 직접 구성한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class BandPerformanceInitializationService {
    private final MembershipService memberships;
    private final EntityManager entityManager;

    /** 활성 정회원을 승인 참여자로 등록하고 모두 기본 팀에 편성한다. */
    public void initialize(Performance performance, Long actorUserId) {
        // 저장된 BAND 공연과 요청자 ID가 모두 준비되었는지 확인한다.
        if (performance == null
                || performance.getId() == null
                || performance.getPreset() != PerformancePreset.BAND
                || actorUserId == null) {
            throw new IllegalArgumentException("BAND 공연 초기화 값이 유효하지 않습니다.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // 외래키의 기준이 될 기본 팀을 먼저 저장해 팀 ID를 확보한다.
        Team team = Team.createDefault(performance, performance.getCreatedBy(), now);
        entityManager.persist(team);
        entityManager.flush();

        // 동아리의 활성 정회원만 자동 참여 대상으로 선정한다.
        List<Membership> eligible = memberships.listAdmin(performance.getClub().getId(), actorUserId).stream()
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .filter(member -> member.getMemberType() == MemberType.REGULAR)
                .sorted(Comparator.comparing(Membership::getId))
                .toList();
        PerformanceMember leader = null;
        // 각 소속을 승인 참여자로 만들고 기본 팀원으로 연결한다.
        for (Membership membership : eligible) {
            PerformanceMember participant = PerformanceMember.addByAdmin(performance, membership, now);
            entityManager.persist(participant);
            entityManager.persist(TeamMember.link(team, participant));
            if (membership.getUser().getId().equals(actorUserId)) {
                leader = participant;
            }
        }
        // 팀원 행을 먼저 저장해야 팀장 복합 외래키를 만족하므로 단계적으로 flush한다.
        entityManager.flush();
        if (leader != null) {
            team.assignLeader(leader);
            entityManager.flush();
        }
    }
}
