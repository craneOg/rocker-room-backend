package com.crane.bandwebbackend.domain.membership.repository;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    @EntityGraph(attributePaths = {"club", "user"})
    Optional<Membership> findByIdAndClub_Id(Long id, Long clubId);

    @EntityGraph(attributePaths = {"club", "user"})
    Optional<Membership> findByClub_IdAndUser_Id(Long clubId, Long userId);

    @EntityGraph(attributePaths = {"club", "user"})
    List<Membership> findByClub_Id(Long clubId);

    @EntityGraph(attributePaths = {"club", "user"})
    List<Membership> findByUser_Id(Long userId);

    boolean existsByClub_IdAndRoleAndStatusIn(
            Long clubId,
            com.crane.bandwebbackend.domain.membership.entity.MembershipRole role,
            java.util.Collection<com.crane.bandwebbackend.domain.membership.entity.MembershipStatus> statuses);

    boolean existsByClub_IdAndIdNotAndRoleAndStatusIn(
            Long clubId,
            Long excludedId,
            com.crane.bandwebbackend.domain.membership.entity.MembershipRole role,
            java.util.Collection<com.crane.bandwebbackend.domain.membership.entity.MembershipStatus> statuses);
}
