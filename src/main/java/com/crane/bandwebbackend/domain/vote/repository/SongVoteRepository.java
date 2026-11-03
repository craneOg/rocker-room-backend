package com.crane.bandwebbackend.domain.vote.repository;

import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import com.crane.bandwebbackend.domain.vote.entity.SongVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SongVoteRepository extends JpaRepository<SongVote, Long> {

    Optional<SongVote> findBySongIdAndPerformanceMemberId(Long songId, Long performanceMemberId);

    @Query("select v from SongVote v join fetch v.submittedBy join fetch v.performanceMember pm"
            + " join fetch pm.membership m join fetch m.user where v.performanceId = :performanceId")
    List<SongVote> findByPerformanceId(Long performanceId);

    @Query("select v from SongVote v join fetch v.submittedBy join fetch v.performanceMember pm"
            + " join fetch pm.membership m join fetch m.user where v.song.id = :songId")
    List<SongVote> findBySongId(Long songId);

    // V-05: 후보곡에 한 찬성 수(철회·탈락 곡의 투표는 세지 않음)
    @Query("select count(v) from SongVote v where v.performanceMember.id = :memberId and v.song.status = :status")
    long countByMemberAndSongStatus(Long memberId, SongStatus status);

    @Modifying(flushAutomatically = true)
    @Query("delete from SongVote v where v.song.id = :songId")
    void deleteBySongId(Long songId);
}
