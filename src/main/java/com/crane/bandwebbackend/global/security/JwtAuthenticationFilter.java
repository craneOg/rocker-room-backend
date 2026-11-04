package com.crane.bandwebbackend.global.security;

import com.crane.bandwebbackend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.crane.bandwebbackend.domain.user.dto.UserAuthInfo;
import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import com.crane.bandwebbackend.domain.user.service.UserAuthQueryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 0.5 / G-02 / G-03: Access 쿠키 -> 서명·만료 -> jti 블랙리스트 -> users PK 1회 조회(token_version, status).
// 어떤 실패든 인증 없이 다음으로 넘기고, 보호 경로에서는 EntryPoint가 401을 쓴다(비인증 허용 경로는 그대로 통과).
// 실패 사유는 DEBUG 로그에만 남긴다
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtProvider jwtProvider;
    private final CookieFactory cookieFactory;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final UserAuthQueryService userAuthQueryService;

    public JwtAuthenticationFilter(
            JwtProvider jwtProvider,
            CookieFactory cookieFactory,
            AccessTokenBlacklistRepository blacklistRepository,
            UserAuthQueryService userAuthQueryService) {
        this.jwtProvider = jwtProvider;
        this.cookieFactory = cookieFactory;
        this.blacklistRepository = blacklistRepository;
        this.userAuthQueryService = userAuthQueryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readAccessToken(request);
        if (token != null) {
            authenticate(token);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        JwtProvider.AccessTokenClaims claims;
        try {
            claims = jwtProvider.parse(token);
        } catch (AccessTokenException e) {
            log.debug("access token rejected: {}", e.getReason());
            return;
        }
        if (blacklistRepository.existsById(claims.getJti())) {
            log.debug("access token rejected: BLACKLISTED");
            return;
        }
        Optional<UserAuthInfo> user = userAuthQueryService.findAuthInfo(claims.getUserId());
        if (user.isEmpty()) {
            log.debug("access token rejected: USER_NOT_FOUND");
            return;
        }
        if (user.get().getTokenVersion() != claims.getTokenVersion()) {
            log.debug("access token rejected: TOKEN_VERSION_MISMATCH"); // G-02
            return;
        }
        if (user.get().getStatus() == UserStatus.WITHDRAWN) {
            log.debug("access token rejected: WITHDRAWN"); // G-03
            return;
        }
        AuthPrincipal principal = new AuthPrincipal(
                claims.getUserId(), claims.getSid(), claims.getJti(), user.get().isSuperAdmin());
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (principal.isSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }

    private String readAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieFactory.accessName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
