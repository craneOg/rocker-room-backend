package com.crane.bandwebbackend.global.storage;

import java.time.Instant;
import java.util.Optional;

// 사진 저장소. 운영은 Cloudflare R2(S3 호환), 테스트는 메모리 구현으로 바꿔 쓴다
public interface ImageStorage {

    void put(String key, byte[] bytes, String contentType);

    // 객체가 없으면 빈 값. 있으면 마지막 수정 시각
    Optional<Instant> lastModified(String key);

    void copy(String sourceKey, String targetKey);

    void delete(String key);

    // 공개 읽기 주소
    String publicUrl(String key);
}
