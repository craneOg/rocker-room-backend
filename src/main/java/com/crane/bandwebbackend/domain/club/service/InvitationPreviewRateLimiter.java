package com.crane.bandwebbackend.domain.club.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// C-17: 로그인 전 초대 미리보기는 IP별 요청 횟수를 제한한다(고정 창). 서버 한 대 전제(0.5)라 메모리에서 센다.
// ponytail: 서버가 여러 대가 되면 인스턴스마다 따로 세므로 한도가 N배가 된다. 그때 공유 저장소로 옮긴다
@Component
public class InvitationPreviewRateLimiter {

    private static class Window {
        private final long startMillis;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Cache<String, Window> windows;

    public InvitationPreviewRateLimiter(
            Clock clock,
            @Value("${app.rate-limit.invitation-preview.limit:30}") int limit,
            @Value("${app.rate-limit.invitation-preview.window:PT1M}") Duration window) {
        this.clock = clock;
        this.limit = limit;
        this.window = window;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(100_000)
                .build();
    }

    // 허용되면 0, 제한되면 다시 시도할 수 있을 때까지 남은 초(1 이상)
    public long tryAcquire(String ip) {
        long now = clock.millis();
        Window current = windows.get(ip, key -> new Window(now));
        if (current.count.incrementAndGet() <= limit) {
            return 0;
        }
        long remainingMillis = current.startMillis + window.toMillis() - now;
        return Math.max(1, (remainingMillis + 999) / 1000);
    }
}
