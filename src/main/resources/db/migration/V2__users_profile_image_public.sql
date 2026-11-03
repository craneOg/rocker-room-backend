-- ERD v6 → v7: 프로필 사진 공개 설정(API-063 공통 규칙)
-- FALSE면 다른 사용자에게 profileImageUrl을 NULL로 반환(임원 포함). 본인·Super Admin은 항상 봄
ALTER TABLE users
    ADD COLUMN profile_image_public BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '[v7] 프로필 사진 공개. FALSE면 본인·Super Admin 외에는 profileImageUrl NULL'
        AFTER profile_image_url;
