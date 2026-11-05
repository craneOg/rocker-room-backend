package com.crane.bandwebbackend.global.storage;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// 사진 저장소(Cloudflare R2, S3 호환) 설정(app.storage.*). 값은 환경 변수로만 주입하고 저장소에 두지 않는다
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        // 예: https://<계정 ID>.r2.cloudflarestorage.com
        @NotBlank String endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKeyId,
        @NotBlank String accessKeyValue,
        // 공개 읽기 주소(커스텀 도메인 또는 r2.dev). 끝의 /는 있어도 된다
        @NotBlank String publicBaseUrl,
        // 임시 키(API 129)로 프로필에 연결할 수 있는 시간
        @DefaultValue("PT1H") Duration tmpTtl) {

    // 로그나 예외 메시지에 접근 키가 그대로 찍히지 않도록 가린다
    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + ", accessKeyId=***,"
                + " accessKeyValue=***, publicBaseUrl=" + publicBaseUrl + ", tmpTtl=" + tmpTtl + "]";
    }
}
