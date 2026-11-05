package com.crane.bandwebbackend.global.storage;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 저장소 목록 조회 결과 한 건
@Getter
@RequiredArgsConstructor
public class StoredObject {

    private final String key;
    private final Instant lastModified;
}
