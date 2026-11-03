package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.dto.UserAuthInfo;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// U6: security가 다른 도메인 repository를 직접 쓰지 않도록 감싼 최소 조회. 캐시 없음(U17)
@Service
@RequiredArgsConstructor
public class UserAuthQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UserAuthInfo> findAuthInfo(long userId) {
        return userRepository
                .findById(userId)
                .map(u -> new UserAuthInfo(u.getTokenVersion(), u.getStatus(), u.isSuperAdmin()));
    }
}
