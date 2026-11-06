package com.crane.bandwebbackend.global.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 사진 저장소. 운영은 Cloudflare R2(S3 호환), 테스트는 메모리 구현으로 바꿔 쓴다
public interface ImageStorage {

    void put(String key, byte[] bytes, String contentType);

    // 객체가 없으면 빈 값. 있으면 마지막 수정 시각
    Optional<Instant> lastModified(String key);

    void copy(String sourceKey, String targetKey);

    void delete(String key);

    // 접두사로 시작하는 모든 객체. 정리 배치용이라 한 번에 메모리로 읽는다
    // ponytail: 객체가 수십만 건을 넘으면 페이지 단위로 처리하도록 바꾼다
    List<StoredObject> list(String prefix);

    // 공개 읽기 주소
    String publicUrl(String key);
}
