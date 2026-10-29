package com.crane.bandwebbackend.domain.availability.entity;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 30분 슬롯 1개당 1행. 승인된 참여자 본인만 입력(대리 입력 불가, 서비스 검증). 저장은 날짜·기간 단위 일괄 교체
@Getter
@Entity
@Table(name = "available_slot")
@IdClass(AvailableSlotId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailableSlot extends BaseCreatedTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id")
    private PerformanceMember performanceMember;

    // ck_available_slot_align: 분은 0 또는 30, 초·마이크로초는 0. 슬롯 = [slotStart, slotStart + 30분)
    @Id
    private LocalDateTime slotStart;
}
