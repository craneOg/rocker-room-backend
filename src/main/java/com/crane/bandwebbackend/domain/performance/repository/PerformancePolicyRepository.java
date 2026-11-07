package com.crane.bandwebbackend.domain.performance.repository;

import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformancePolicyRepository extends JpaRepository<PerformancePolicy, Long> {}
