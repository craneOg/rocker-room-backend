package com.crane.bandwebbackend.domain.song.repository;

import com.crane.bandwebbackend.domain.song.entity.SongPartRequirement;
import com.crane.bandwebbackend.domain.song.entity.SongPartRequirementId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SongPartRequirementRepository extends JpaRepository<SongPartRequirement, SongPartRequirementId> {

    @Query("select r from SongPartRequirement r join fetch r.part where r.song.id = :songId order by r.part.displayOrder")
    List<SongPartRequirement> findBySongId(Long songId);

    @Query("select r from SongPartRequirement r join fetch r.part where r.song.performance.id = :performanceId")
    List<SongPartRequirement> findByPerformanceId(Long performanceId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from SongPartRequirement r where r.song.id = :songId")
    void deleteBySongId(Long songId);
}
