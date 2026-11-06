package com.crane.bandwebbackend.global.storage;

import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 업로드 사진 검사(U-12): 2MiB 이하, JPG·PNG·WEBP만, 선언한 Content-Type과 파일 헤더 시그니처가 같아야 한다.
// JPG·PNG는 실제로 디코딩한다. WebP는 Java에 기본 디코더가 없어 RIFF 헤더의 형식·크기만 확인한다(새 라이브러리를 쓰지 않는다)
@Getter
@RequiredArgsConstructor
public class UploadedImage {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    // 디코딩 폭탄 방지: 한 변과 전체 픽셀 수의 상한
    // 프로필 사진 용도에 맞춰 낮게 잡는다(전체 디코딩 시 픽셀당 4바이트 안팎의 메모리를 쓴다)
    private static final int MAX_SIDE = 4_096;
    private static final long MAX_PIXELS = 4_096L * 4_096L;

    private final String extension;
    private final String contentType;

    // 실패하면 400 VALIDATION_ERROR(field file, reason
    // FILE_TOO_LARGE·UNSUPPORTED_MEDIA_TYPE·UNSUPPORTED_FORMAT·INVALID_IMAGE)
    public static UploadedImage validate(byte[] bytes, String declaredContentType) {
        if (bytes.length > MAX_BYTES) {
            throw invalid("FILE_TOO_LARGE");
        }
        String declared =
                declaredContentType == null ? "" : declaredContentType.strip().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(declared)) {
            throw invalid("UNSUPPORTED_MEDIA_TYPE");
        }
        if (bytes.length == 0) {
            throw invalid("INVALID_IMAGE");
        }
        UploadedImage detected = detect(bytes);
        if (detected == null || !detected.contentType.equals(declared)) {
            throw invalid("UNSUPPORTED_FORMAT");
        }
        boolean ok = "image/webp".equals(declared) ? webpSizeOk(bytes) : decodes(bytes);
        if (!ok) {
            throw invalid("INVALID_IMAGE");
        }
        return detected;
    }

    private static UploadedImage detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new UploadedImage("jpg", "image/jpeg");
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A) {
            return new UploadedImage("png", "image/png");
        }
        if (b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) {
            return new UploadedImage("webp", "image/webp");
        }
        return null;
    }

    // JPG·PNG: 크기를 먼저 확인한 뒤 실제로 디코딩한다
    private static boolean decodes(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (!sizeOk(reader.getWidth(0), reader.getHeight(0))) {
                    return false;
                }
                return reader.read(0) != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // WebP: RIFF 크기가 실제 길이와 맞고, VP8(손실)·VP8L(무손실)·VP8X(확장) 헤더에서 읽은 가로·세로가 상한 안이어야 한다
    private static boolean webpSizeOk(byte[] b) {
        if (b.length < 30 || le32(b, 4) + 8L != b.length) {
            return false;
        }
        int width;
        int height;
        if (ascii(b, 12, "VP8 ")) {
            // 프레임 시작 코드 9D 01 2A 뒤에 14비트 가로·세로
            if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) {
                return false;
            }
            width = le16(b, 26) & 0x3FFF;
            height = le16(b, 28) & 0x3FFF;
        } else if (ascii(b, 12, "VP8L")) {
            if ((b[20] & 0xFF) != 0x2F) {
                return false;
            }
            int bits = le32(b, 21);
            width = (bits & 0x3FFF) + 1;
            height = ((bits >> 14) & 0x3FFF) + 1;
        } else if (ascii(b, 12, "VP8X")) {
            width = le24(b, 24) + 1;
            height = le24(b, 27) + 1;
        } else {
            return false;
        }
        return sizeOk(width, height);
    }

    private static boolean sizeOk(int width, int height) {
        return width > 0
                && height > 0
                && width <= MAX_SIDE
                && height <= MAX_SIDE
                && (long) width * height <= MAX_PIXELS;
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (b[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int le24(byte[] b, int o) {
        return le16(b, o) | ((b[o + 2] & 0xFF) << 16);
    }

    private static int le32(byte[] b, int o) {
        return le24(b, o) | ((b[o + 3] & 0xFF) << 24);
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", List.of(ErrorDetail.of("file", reason)));
    }
}
