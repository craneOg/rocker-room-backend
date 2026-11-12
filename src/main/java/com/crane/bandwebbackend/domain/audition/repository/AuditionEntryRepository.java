package com.crane.bandwebbackend.domain.audition.repository;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditionEntryRepository extends JpaRepository<AuditionEntry, Long> {
    @EntityGraph(
            attributePaths = {
                "song",
                "song.candidateTeam",
                "performanceMember",
                "performanceMember.membership",
                "performanceMember.membership.user",
                "part",
                "practiceSchedule",
                "reviewedBy"
            })
    Optional<AuditionEntry> findByIdAndPerformanceId(Long id, Long performanceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditionEntry> findWithLockByIdAndPerformanceId(Long id, Long performanceId);

    /** A-06: 목록용 스냅샷 조회와 분리한 최신 잠금 읽기. 호출자는 공연 → 곡을 먼저 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuditionEntry a where a.performanceId = :performanceId and a.song.id = :songId"
            + " and a.performanceMember is null and a.result is null")
    List<AuditionEntry> findPendingSongWithLock(
            @Param("performanceId") Long performanceId, @Param("songId") Long songId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuditionEntry a where a.performanceId = :performanceId and a.song.id = :songId"
            + " and a.part.id = :partId and a.performanceMember.id in :applicantIds and a.result is null")
    List<AuditionEntry> findPendingSessionsWithLock(
            @Param("performanceId") Long performanceId,
            @Param("songId") Long songId,
            @Param("partId") Long partId,
            @Param("applicantIds") Collection<Long> applicantIds);

    @EntityGraph(
            attributePaths = {
                "song",
                "song.candidateTeam",
                "performanceMember",
                "performanceMember.membership",
                "performanceMember.membership.user",
                "part",
                "practiceSchedule",
                "reviewedBy"
            })
    List<AuditionEntry> findByPerformanceId(Long performanceId);

    @EntityGraph(
            attributePaths = {
                "song",
                "song.candidateTeam",
                "performanceMember",
                "performanceMember.membership",
                "performanceMember.membership.user",
                "part",
                "practiceSchedule",
                "reviewedBy"
            })
    List<AuditionEntry> findByPerformanceMember_Membership_User_Id(Long userId);

    @EntityGraph(
            attributePaths = {
                "song",
                "song.candidateTeam",
                "performanceMember",
                "performanceMember.membership",
                "performanceMember.membership.user",
                "part",
                "practiceSchedule",
                "reviewedBy"
            })
    List<AuditionEntry> findBySong_ProposedBy_User_IdAndPerformanceMemberIsNull(Long userId);
}
