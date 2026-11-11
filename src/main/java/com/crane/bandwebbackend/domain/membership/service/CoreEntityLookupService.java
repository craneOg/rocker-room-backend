package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 동아리와 활성 사용자를 위한 기본 영속성 조회 어댑터. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CoreEntityLookupService {
    private final EntityManager entityManager;

    /** 식별자에 해당하는 동아리를 조회하고 없으면 NOT_FOUND를 반환한다. */
    public Club require(Long clubId) {
        // 영속성 컨텍스트를 우선 사용하고 없으면 데이터베이스에서 동아리를 조회한다.
        Club club = entityManager.find(Club.class, clubId);
        // 존재하지 않는 식별자는 이후 서비스가 null을 다루지 않도록 즉시 404로 변환한다.
        if (club == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다.");
        }
        return club;
    }

    /** 식별자에 해당하는 활성 사용자를 조회한다. */
    public User requireActive(Long userId) {
        // 사용자 존재 여부와 서비스 탈퇴 여부를 한 번에 확인한다.
        User user = entityManager.find(User.class, userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "활성 사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    /** 정확한 핸들에 해당하는 활성 사용자를 조회한다. */
    public User byHandle(String handle) {
        // 빈 핸들로 전체 사용자 검색이 수행되지 않도록 입력을 먼저 차단한다.
        if (handle == null || handle.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "handle이 필요합니다.");
        }
        // 고유 핸들과 ACTIVE 상태를 함께 조건으로 사용해 초대 가능한 사용자만 찾는다.
        List<User> users = entityManager
                .createQuery("""
				SELECT user FROM User user
				WHERE user.handle = :handle AND user.status = :status
				""", User.class)
                .setParameter("handle", handle)
                .setParameter("status", UserStatus.ACTIVE)
                .setMaxResults(1)
                .getResultList();
        if (users.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "활성 사용자를 찾을 수 없습니다.");
        }
        return users.get(0);
    }
}
