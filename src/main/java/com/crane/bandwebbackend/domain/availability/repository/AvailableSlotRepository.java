package com.crane.bandwebbackend.domain.availability.repository;

import com.crane.bandwebbackend.domain.availability.entity.AvailableSlot;
import com.crane.bandwebbackend.domain.availability.entity.AvailableSlotId;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AvailableSlotRepository extends JpaRepository<AvailableSlot, AvailableSlotId> {

    // 기간 [from, to) 단위 일괄 교체(AV-03)
    @Modifying(flushAutomatically = true)
    @Query("delete from AvailableSlot s where s.performanceMember.id = :memberId"
            + " and s.slotStart >= :from and s.slotStart < :to")
    void deleteInRange(Long memberId, LocalDateTime from, LocalDateTime to);

    @Query("select s.slotStart from AvailableSlot s where s.performanceMember.id = :memberId"
            + " and s.slotStart >= :from and s.slotStart < :to order by s.slotStart")
    List<LocalDateTime> findStartsInRange(Long memberId, LocalDateTime from, LocalDateTime to);

    @Query("select s.slotStart from AvailableSlot s where s.performanceMember.id = :memberId order by s.slotStart")
    List<LocalDateTime> findStarts(Long memberId);

    @Query("select s from AvailableSlot s where s.performanceMember.id in :memberIds"
            + " and s.slotStart >= :from and s.slotStart < :to")
    List<AvailableSlot> findByMembersInRange(Collection<Long> memberIds, LocalDateTime from, LocalDateTime to);

    // 한 번이라도 입력한 참여자(respondedCount)
    @Query("select distinct s.performanceMember.id from AvailableSlot s where s.performanceMember.id in :memberIds")
    List<Long> findRespondedMemberIds(Collection<Long> memberIds);
}
