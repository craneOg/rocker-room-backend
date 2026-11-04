package com.crane.bandwebbackend.global.security;

// 인증된 요청의 주체. 클럽 내 역할은 토큰이 아니라 요청마다 membership으로 확인한다(G-07)
public record AuthPrincipal(long userId, String sid, String jti, boolean isSuperAdmin) {}
