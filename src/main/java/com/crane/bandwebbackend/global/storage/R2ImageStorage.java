package com.crane.bandwebbackend.global.storage;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

// Cloudflare R2 구현. R2는 리전이 auto이고 경로 방식 주소를 쓰며, 체크섬은 필요한 경우에만 계산한다(R2 권장 설정)
public class R2ImageStorage implements ImageStorage, AutoCloseable {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public R2ImageStorage(StorageProperties properties) {
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyId(), properties.accessKeyValue())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.bucket = properties.bucket();
        String base = properties.publicBaseUrl().strip();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<Instant> lastModified(String key) {
        try {
            return Optional.of(s3.headObject(
                            HeadObjectRequest.builder().bucket(bucket).key(key).build())
                    .lastModified());
        } catch (S3Exception e) {
            // HeadObject의 404는 본문이 없어 R2에서는 NoSuchKeyException이 아닌 일반 S3Exception으로 올 수 있다
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(targetKey)
                .build());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public List<StoredObject> list(String prefix) {
        List<StoredObject> out = new ArrayList<>();
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build())
                .contents()
                .forEach(o -> out.add(new StoredObject(o.key(), o.lastModified())));
        return out;
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void close() {
        s3.close();
    }
}
