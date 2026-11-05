package com.crane.bandwebbackend.domain.admin.repository;

import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 운영 계정 조회(API 122)가 사용자별 소속 동아리와 카카오 연결 여부를 읽는 조회 전용 저장소
public interface AdminUserQueryRepository extends Repository<Membership, Long> {

    interface UserClubRow {
        Long getUserId();

        Long getClubId();

        String getClubName();

        MembershipRole getRole();

        MembershipStatus getStatus();

        JoinSource getJoinSource();
    }

    @Query("select m.user.id as userId, m.club.id as clubId, m.club.name as clubName, m.role as role,"
            + " m.status as status, m.joinSource as joinSource from Membership m"
            + " where m.user.id in :userIds order by m.club.name asc, m.club.id asc")
    List<UserClubRow> findClubs(@Param("userIds") Collection<Long> userIds);

    // providerUid 자체는 읽지 않는다. 연결된 사용자 ID만 가져온다
    @Query("select i.user.id from AuthIdentity i where i.user.id in :userIds")
    List<Long> findKakaoLinkedUserIds(@Param("userIds") Collection<Long> userIds);
}
