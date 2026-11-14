package com.crane.bandwebbackend.domain.part.repository;

import com.crane.bandwebbackend.domain.part.entity.MembershipPart;
import com.crane.bandwebbackend.domain.part.entity.MembershipPartId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPartRepository extends JpaRepository<MembershipPart, MembershipPartId> {
    List<MembershipPart> findByMembership_Id(Long membershipId);

    List<MembershipPart> findByMembership_IdIn(List<Long> membershipIds);
}
