package com.crane.bandwebbackend.domain.part.repository;

import com.crane.bandwebbackend.domain.part.entity.Part;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, Long> {
    List<Part> findByClub_IdOrderByDisplayOrderAscIdAsc(Long clubId);

    Optional<Part> findByIdAndClub_Id(Long id, Long clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Part> findWithLockByIdAndClub_Id(Long id, Long clubId);

    boolean existsByClub_IdAndName(Long clubId, String name);

    @Query(value = """
            SELECT mp.part_id FROM membership_part mp WHERE mp.membership_id = :membershipId
            UNION SELECT tm.part_id FROM team_member tm
                  JOIN performance_member pm ON pm.id = tm.performance_member_id
                  WHERE pm.membership_id = :membershipId AND tm.part_id IS NOT NULL
            UNION SELECT ssm.part_id FROM song_session_member ssm
                  JOIN performance_member pm ON pm.id = ssm.performance_member_id
                  WHERE pm.membership_id = :membershipId
            UNION SELECT sp.part_id FROM song_playability sp
                  JOIN performance_member pm ON pm.id = sp.performance_member_id
                  WHERE pm.membership_id = :membershipId
            UNION SELECT ae.part_id FROM audition_entry ae
                  JOIN performance_member pm ON pm.id = ae.performance_member_id
                  WHERE pm.membership_id = :membershipId AND ae.part_id IS NOT NULL
            """, nativeQuery = true)
    List<Long> findHistoricallyReferencedPartIds(@Param("membershipId") Long membershipId);
}
