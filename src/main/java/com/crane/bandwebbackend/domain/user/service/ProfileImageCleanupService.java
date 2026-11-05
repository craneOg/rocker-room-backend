package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.repository.ProfileImageReferenceRepository;
import com.crane.bandwebbackend.global.storage.ImageStorage;
import com.crane.bandwebbackend.global.storage.StorageProperties;
import com.crane.bandwebbackend.global.storage.StoredObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 저장소의 고아 사진 정리. 두 가지를 지운다.
//  1) 임시 객체(tmp/): 업로드 후 연결 가능 시간(tmpTtl)이 지난 것. 그 뒤에는 어떤 요청도 연결할 수 없다
//  2) 영구 객체(users/): 현재 프로필·탈퇴 보관 어디에서도 참조하지 않고, 생성 후 유예 시간이 지난 것
// 지우는 쪽이 실수하면 사용자 사진이 사라지므로 보수적으로 동작한다: 참조 확인이 실패하면 아무것도 지우지 않고,
// 한 번에 지우는 수를 제한하며, 공개 주소 설정이 바뀐 것으로 보이는 상황(후보가 많은데 참조가 하나도 없음)에서는 중단한다
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageCleanupService {

    private static final String TMP_PREFIX = "tmp/";
    private static final String PERMANENT_PREFIX = "users/";
    // 복사(영구 객체 생성)와 DB 커밋 사이의 진행 중인 요청을 지우지 않도록 두는 유예
    static final Duration ORPHAN_GRACE = Duration.ofHours(1);
    // 한 번 실행에 지우는 최대 수. 남은 것은 다음 실행에서 지운다
    static final int MAX_DELETES_PER_RUN = 500;
    // 후보가 이 수 이상인데 참조가 하나도 없으면 공개 주소 설정 불일치로 보고 영구 객체 삭제를 중단한다
    static final int MISCONFIG_GUARD_MIN_CANDIDATES = 20;
    private static final int LOOKUP_BATCH = 500;

    private final ImageStorage storage;
    private final ProfileImageReferenceRepository references;
    private final StorageProperties properties;
    private final Clock clock;

    @Getter
    @ToString
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class Result {

        private final int expiredTmpDeleted;
        private final int orphansDeleted;
        // 영구 객체 삭제를 안전장치로 중단했는지
        private final boolean orphanScanAborted;
    }

    public Result cleanup() {
        Instant now = Instant.now(clock);
        int tmp = deleteExpiredTmp(now);
        List<String> orphans = findOrphans(now);
        if (orphans == null) {
            return new Result(tmp, 0, true);
        }
        int budget = Math.max(0, MAX_DELETES_PER_RUN - tmp);
        int deleted = 0;
        for (String key : orphans.subList(0, Math.min(orphans.size(), budget))) {
            if (deleteQuietly(key)) {
                deleted++;
            }
        }
        if (orphans.size() > budget) {
            log.warn("고아 사진이 한 번에 지울 수 있는 수를 넘었습니다. 남은 {}건은 다음 실행에서 지웁니다", orphans.size() - budget);
        }
        return new Result(tmp, deleted, false);
    }

    private int deleteExpiredTmp(Instant now) {
        Instant cutoff = now.minus(properties.tmpTtl());
        int deleted = 0;
        for (StoredObject o : storage.list(TMP_PREFIX)) {
            if (deleted >= MAX_DELETES_PER_RUN) {
                break;
            }
            if (o.getLastModified().isBefore(cutoff) && deleteQuietly(o.getKey())) {
                deleted++;
            }
        }
        return deleted;
    }

    // 지워도 되는 영구 객체의 키. 참조 확인 중 오류가 나면 예외를 그대로 던져 아무것도 지우지 않는다. 중단하면 null
    private List<String> findOrphans(Instant now) {
        Instant cutoff = now.minus(ORPHAN_GRACE);
        List<String> candidates = new ArrayList<>();
        for (StoredObject o : storage.list(PERMANENT_PREFIX)) {
            if (o.getLastModified().isBefore(cutoff)) {
                candidates.add(o.getKey());
            }
        }
        Set<String> referenced = new HashSet<>();
        for (int i = 0; i < candidates.size(); i += LOOKUP_BATCH) {
            List<String> urls = candidates.subList(i, Math.min(candidates.size(), i + LOOKUP_BATCH)).stream()
                    .map(storage::publicUrl)
                    .toList();
            referenced.addAll(references.findReferenced(urls));
        }
        if (candidates.size() >= MISCONFIG_GUARD_MIN_CANDIDATES && referenced.isEmpty()) {
            log.error(
                    "사진 {}건 중 참조되는 것이 하나도 없어 정리를 중단합니다. 공개 주소(app.storage.public-base-url) 설정을 확인하세요",
                    candidates.size());
            return null;
        }
        return candidates.stream()
                .filter(key -> !referenced.contains(storage.publicUrl(key)))
                .toList();
    }

    private boolean deleteQuietly(String key) {
        try {
            storage.delete(key);
            return true;
        } catch (RuntimeException e) {
            log.warn("사진 객체 삭제 실패 key={}", key, e);
            return false;
        }
    }
}
