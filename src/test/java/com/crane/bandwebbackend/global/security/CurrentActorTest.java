package com.crane.bandwebbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorTest {
    private final CurrentActor currentActor = new CurrentActor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsJwtAuthenticatedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal(17L, "sid", "jti", false), null, List.of()));

        assertThat(currentActor.requireUserId()).isEqualTo(17L);
    }

    @Test
    void rejectsNumericAuthenticatedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(17L, null, List.of()));

        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void rejectsAuthenticationWithoutAuthPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("17", null, List.of()));

        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
