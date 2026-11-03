package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.UserWithdrawalArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWithdrawalArchiveRepository extends JpaRepository<UserWithdrawalArchive, Long> {}
