package com.crane.bandwebbackend.global.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 테스트용 메모리 저장소. 실제 R2에 접속하지 않는다
public class InMemoryImageStorage implements ImageStorage {

    public static final String PUBLIC_BASE = "https://cdn.test.example";

    public static class Stored {
        public byte[] bytes;
        public String contentType;
        public Instant modifiedAt;
    }

    private final Map<String, Stored> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Stored s = new Stored();
        s.bytes = bytes;
        s.contentType = contentType;
        s.modifiedAt = Instant.now();
        objects.put(key, s);
    }

    @Override
    public Optional<Instant> lastModified(String key) {
        return Optional.ofNullable(objects.get(key)).map(s -> s.modifiedAt);
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        Stored src = objects.get(sourceKey);
        if (src == null) {
            throw new IllegalStateException("no such key " + sourceKey);
        }
        Stored s = new Stored();
        s.bytes = src.bytes;
        s.contentType = src.contentType;
        s.modifiedAt = Instant.now();
        objects.put(targetKey, s);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public String publicUrl(String key) {
        return PUBLIC_BASE + "/" + key;
    }

    public Stored get(String key) {
        return objects.get(key);
    }

    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    public int size() {
        return objects.size();
    }

    public void clear() {
        objects.clear();
    }
}
