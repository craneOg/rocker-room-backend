package com.crane.bandwebbackend.domain.club.repository;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 동아리 검색·가입 코드 확인이 "내 소속 상태"만 읽을 때 쓰는 조회 전용 저장소(소속을 바꾸지 않는다)
public interface ClubApplicationStatusRepository extends Repository<Membership, Long> {

    interface ClubMembershipStatus {
        Long getClubId();

        MembershipStatus getStatus();
    }

    @Query("select m.club.id as clubId, m.status as status from Membership m"
            + " where m.user.id = :userId and m.club.id in :clubIds")
    List<ClubMembershipStatus> findStatuses(@Param("userId") Long userId, @Param("clubIds") Collection<Long> clubIds);
}
