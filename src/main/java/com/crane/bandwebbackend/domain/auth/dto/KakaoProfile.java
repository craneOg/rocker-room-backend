package com.crane.bandwebbackend.domain.auth.dto;

// 카카오에서 받은 값 중 우리가 쓰는 것만. nickname·imageUrl은 비어 있을 수 있다
public record KakaoProfile(String id, String nickname, String imageUrl) {}
