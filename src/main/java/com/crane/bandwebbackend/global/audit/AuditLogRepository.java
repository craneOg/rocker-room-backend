package com.crane.bandwebbackend.global.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// 서비스 감사 로그 조회(API 125)가 선택 조건을 조합하려고 Specification을 쓴다
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {}
