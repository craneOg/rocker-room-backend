package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 다른 도메인(운영 계정 조회 등)이 사용자를 찾을 때 쓰는 진입점. user 저장소를 직접 쓰지 않게 한다
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<User> findById(long userId) {
        return userRepository.findById(userId);
    }

    // handle은 소문자로만 저장되므로 정확 일치로 찾는다
    @Transactional(readOnly = true)
    public Optional<User> findByHandle(String handle) {
        return userRepository.findByHandle(handle);
    }
}
