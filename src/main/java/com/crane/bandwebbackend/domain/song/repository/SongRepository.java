package com.crane.bandwebbackend.domain.song.repository;

import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SongRepository extends JpaRepository<Song, Long> {

    Optional<Song> findByIdAndPerformanceId(Long id, Long performanceId);

    // 카드에 팀 이름·제안자 이름을 함께 담으므로 한 번에(N+1 방지)
    @Query("select s from Song s left join fetch s.candidateTeam left join fetch s.assignedTeam"
            + " join fetch s.proposedBy m join fetch m.user where s.performance.id = :performanceId order by s.id")
    List<Song> findAllWithRefs(Long performanceId);

    List<Song> findByPerformanceIdAndStatusInOrderById(Long performanceId, Collection<SongStatus> statuses);

    List<Song> findByCandidateTeamIdAndStatus(Long teamId, SongStatus status);

    long countByAssignedTeamIdAndStatus(Long teamId, SongStatus status);

    long countByCandidateTeamIdAndStatus(Long teamId, SongStatus status);

    // S-03: 한도 계산에서 철회 곡 제외
    long countByPerformanceIdAndProposedByIdAndStatusNot(Long performanceId, Long membershipId, SongStatus status);

    boolean existsByPerformanceIdAndTitleAndArtistAndStatusNot(
            Long performanceId, String title, String artist, SongStatus status);
}
