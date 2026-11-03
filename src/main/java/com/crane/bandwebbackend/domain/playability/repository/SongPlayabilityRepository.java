package com.crane.bandwebbackend.domain.playability.repository;

import com.crane.bandwebbackend.domain.playability.entity.SongPlayability;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SongPlayabilityRepository extends JpaRepository<SongPlayability, Long> {

    Optional<SongPlayability> findBySongIdAndPerformanceMemberIdAndPartId(
            Long songId, Long performanceMemberId, Long partId);

    @Query(
            "select p from SongPlayability p join fetch p.part join fetch p.submittedBy join fetch p.performanceMember pm"
                    + " join fetch pm.membership m join fetch m.user where p.performanceId = :performanceId")
    List<SongPlayability> findByPerformanceId(Long performanceId);

    @Query(
            "select p from SongPlayability p join fetch p.part join fetch p.submittedBy join fetch p.performanceMember pm"
                    + " join fetch pm.membership m join fetch m.user where p.song.id = :songId")
    List<SongPlayability> findBySongId(Long songId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SongPlayability p where p.song.id = :songId")
    void deleteBySongId(Long songId);
}
