package com.crane.bandwebbackend.domain.part.entity;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부원이 다룰 수 있는 파트. 같은 동아리 파트인지는 서비스 검증
@Getter
@Entity
@Table(name = "membership_part")
@IdClass(MembershipPartId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPart {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;
}
