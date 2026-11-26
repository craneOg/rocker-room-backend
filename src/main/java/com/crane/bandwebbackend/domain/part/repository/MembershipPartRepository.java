package com.crane.bandwebbackend.domain.part.repository;

import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.entity.MembershipPart;
import com.crane.bandwebbackend.domain.part.entity.MembershipPartId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPartRepository extends JpaRepository<MembershipPart, MembershipPartId> {
    List<MembershipPart> findByMembership_Id(Long membershipId);

    List<MembershipPart> findByMembership_IdIn(List<Long> membershipIds);

    void deleteByMembership_Id(Long membershipId);

    @Query("""
            SELECT mp.part.id, COUNT(mp) FROM MembershipPart mp
            WHERE mp.part.club.id = :clubId AND mp.membership.status IN :statuses
            GROUP BY mp.part.id
            """)
    List<Object[]> countMembersByPart(
            @Param("clubId") Long clubId, @Param("statuses") Collection<MembershipStatus> statuses);
}
