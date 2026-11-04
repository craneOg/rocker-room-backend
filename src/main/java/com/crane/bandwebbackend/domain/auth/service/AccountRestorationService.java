package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.dto.LoginResult;
import com.crane.bandwebbackend.domain.auth.dto.RestorationInfo;
import com.crane.bandwebbackend.domain.auth.dto.RestorationResult;
import com.crane.bandwebbackend.domain.auth.entity.AuthIdentity;
import com.crane.bandwebbackend.domain.auth.entity.AuthProvider;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import com.crane.bandwebbackend.domain.auth.repository.AuthIdentityRepository;
import com.crane.bandwebbackend.domain.auth.repository.OneTimeTokenRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.entity.UserWithdrawalArchive;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.domain.user.repository.UserWithdrawalArchiveRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 131~133: 탈퇴 후 90일 이내 계정의 복구 선택(U-10). 복구 선택 쿠키(ACCOUNT_RESTORATION)로만 호출한다
@Service
@RequiredArgsConstructor
public class AccountRestorationService {

    static final Duration RESTORABLE_PERIOD = Duration.ofDays(90);
    static final String COOKIE_INVALID = "RESTORATION_COOKIE_INVALID";
    static final String NOT_RESTORABLE = "NOT_RESTORABLE";

    private final OneTimeTokenService oneTimeTokens;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final UserRepository userRepository;
    private final UserWithdrawalArchiveRepository archiveRepository;
    private final AuthIdentityRepository identityRepository;
    private final AuthSessionService sessions;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Getter
    @RequiredArgsConstructor
    public static class Result {

        private final RestorationResult body;
        private final AuthSessionService.Tokens tokens;
    }

    // 쿠키를 소비하지 않는다. 사용자가 선택하기 전에 화면에서 여러 번 읽을 수 있다
    @Transactional(readOnly = true)
    public RestorationInfo info(String restorationToken) {
        OneTimeTokenService.Consumed token = oneTimeTokens
                .peek(restorationToken, OneTimeTokenPurpose.ACCOUNT_RESTORATION)
                .orElseThrow(AccountRestorationService::cookieInvalid);
        UserWithdrawalArchive archive =
                archiveRepository.findById(token.getUserId()).orElseThrow(AccountRestorationService::cookieInvalid);
        return new RestorationInfo(
                archive.getName(),
                archive.getProfileImageUrl(),
                KstTime.toKst(archive.getWithdrawnAt()),
                KstTime.toKst(archive.getWithdrawnAt().plus(RESTORABLE_PERIOD)));
    }

    // 보관한 이름·사진으로 같은 계정을 되살린다. 소속·역할·참여와 handle은 복구하지 않는다
    @Transactional
    public Result restore(String restorationToken, String ip, String userAgent) {
        User user = consumeAndLockWithdrawnUser(restorationToken);
        UserWithdrawalArchive archive =
                archiveRepository.findById(user.getId()).orElseThrow(AccountRestorationService::notRestorable);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(archive.getWithdrawnAt().plus(RESTORABLE_PERIOD))) {
            throw notRestorable();
        }
        user.restore(archive.getName(), archive.getProfileImageUrl());
        archiveRepository.delete(archive);
        auditRecorder.record(
                null,
                user.getId(),
                "ACCOUNT_RESTORED",
                "USER",
                user.getId(),
                null,
                Map.of("before", Map.of("status", "WITHDRAWN"), "after", Map.of("status", "ACTIVE")));
        AuthSessionService.Tokens tokens = sessions.start(user, ip, userAgent);
        return new Result(new RestorationResult(LoginResult.EXISTING_USER), tokens);
    }

    // 보관 데이터를 파기하고, 같은 카카오 계정을 새 사용자에 연결한다. 기존 사용자 행은 "탈퇴한 사용자"로 남는다
    @Transactional
    public Result startNew(String restorationToken, String ip, String userAgent) {
        OneTimeTokenService.Consumed token = oneTimeTokens
                .consume(restorationToken, OneTimeTokenPurpose.ACCOUNT_RESTORATION)
                .orElseThrow(AccountRestorationService::cookieInvalid);
        User old = lockWithdrawnUser(token.getUserId());
        AuthIdentity identity = identityRepository
                .findByUserIdAndProvider(old.getId(), AuthProvider.KAKAO)
                .orElseThrow(AccountRestorationService::notRestorable);
        String providerUid = identity.getProviderUid();

        archiveRepository.findById(old.getId()).ifPresent(archiveRepository::delete);
        identityRepository.delete(identity);
        identityRepository.flush();
        oneTimeTokenRepository.deleteUnusedByUser(old.getId());

        User fresh = userRepository.save(User.createFromProvider(
                KakaoAccountService.name(token.getProfileName()),
                KakaoAccountService.imageUrl(token.getProfileImageUrl())));
        identityRepository.save(AuthIdentity.create(fresh, AuthProvider.KAKAO, providerUid));
        auditRecorder.record(
                null, old.getId(), "ACCOUNT_PURGED", "USER", old.getId(), null, Map.of("newUserId", fresh.getId()));

        AuthSessionService.Tokens tokens = sessions.start(fresh, ip, userAgent);
        return new Result(new RestorationResult(LoginResult.NEW_USER), tokens);
    }

    private User consumeAndLockWithdrawnUser(String restorationToken) {
        OneTimeTokenService.Consumed token = oneTimeTokens
                .consume(restorationToken, OneTimeTokenPurpose.ACCOUNT_RESTORATION)
                .orElseThrow(AccountRestorationService::cookieInvalid);
        return lockWithdrawnUser(token.getUserId());
    }

    private User lockWithdrawnUser(Long userId) {
        if (userId == null) {
            throw cookieInvalid();
        }
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AccountRestorationService::cookieInvalid);
        if (user.getStatus() != UserStatus.WITHDRAWN) {
            throw notRestorable();
        }
        return user;
    }

    private static BusinessException cookieInvalid() {
        return new BusinessException(
                ErrorCode.UNAUTHENTICATED,
                "복구 선택 정보가 없거나 만료되었습니다.",
                List.of(ErrorDetail.of("request", COOKIE_INVALID)));
    }

    private static BusinessException notRestorable() {
        return new BusinessException(
                ErrorCode.STATE_CONFLICT, "복구할 수 없는 상태입니다.", List.of(ErrorDetail.of("request", NOT_RESTORABLE)));
    }
}
