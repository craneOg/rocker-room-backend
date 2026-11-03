package com.crane.bandwebbackend.domain.song.repository;

import com.crane.bandwebbackend.domain.song.entity.SongSessionMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SongSessionMemberRepository extends JpaRepository<SongSessionMember, Long> {

    @Query("select m from SongSessionMember m join fetch m.part join fetch m.performanceMember pm"
            + " join fetch pm.membership ms join fetch ms.user where m.song.id = :songId order by m.id")
    List<SongSessionMember> findBySongId(Long songId);

    @Query("select m from SongSessionMember m join fetch m.part join fetch m.performanceMember pm"
            + " join fetch pm.membership ms join fetch ms.user where m.performanceId = :performanceId order by m.id")
    List<SongSessionMember> findByPerformanceId(Long performanceId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from SongSessionMember m where m.song.id = :songId")
    void deleteBySongId(Long songId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from SongSessionMember m where m.performanceMember.id = :memberId and m.song.id in :songIds")
    void deleteByMemberAndSongs(Long memberId, Collection<Long> songIds);
}
