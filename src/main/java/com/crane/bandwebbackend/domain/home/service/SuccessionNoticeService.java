package com.crane.bandwebbackend.domain.home.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.home.entity.SuccessionNotice;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** U-06: 승계 트랜잭션에 참여해 API-135가 읽을 미확인 안내를 남긴다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SuccessionNoticeService {
    private final EntityManager entityManager;

    public void admin(Long clubId, Long successorUserId) {
        entityManager.persist(SuccessionNotice.admin(
                entityManager.getReference(User.class, successorUserId),
                entityManager.getReference(Club.class, clubId)));
    }

    public void teamLeader(Long clubId, Long performanceId, Long teamId, Long successorUserId) {
        entityManager.persist(SuccessionNotice.teamLeader(
                entityManager.getReference(User.class, successorUserId),
                entityManager.getReference(Club.class, clubId),
                entityManager.getReference(Team.class, teamId),
                performanceId));
    }
}
