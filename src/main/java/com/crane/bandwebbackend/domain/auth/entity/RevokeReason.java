package com.crane.bandwebbackend.domain.auth.entity;

public enum RevokeReason {
    ROTATED,
    LOGOUT,
    LOGOUT_ALL,
    FORCE_LOGOUT,
    REUSE_DETECTED,
    WITHDRAWN
}
