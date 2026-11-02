package com.crane.bandwebbackend.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 테스트 전용 최소 컨트롤러: 보호 경로의 GET·POST 동작 확인용
@RestController
@RequestMapping("/api/v1/_test")
class SecurityTestController {

    @GetMapping("/protected")
    String read(@AuthenticationPrincipal AuthPrincipal principal) {
        return String.valueOf(principal.userId());
    }

    @PostMapping("/write")
    String write() {
        return "ok";
    }
}
