package com.crane.bandwebbackend.domain.performance.repository;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface PerformanceRepository extends JpaRepository<Performance, Long>, JpaSpecificationExecutor<Performance> {
	Optional<Performance> findByIdAndClub_Id(Long id, Long clubId);

	List<Performance> findByClub_Id(Long clubId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Performance> findWithLockByIdAndClub_Id(Long id, Long clubId);
}
