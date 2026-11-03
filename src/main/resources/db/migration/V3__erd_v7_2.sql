-- ERD v7.1 → v7.2 (docs/erd_v7_2.sql). V1·V2는 수정하지 않고 변경분만 담는다.
--  * users.profile_image_public 추가(프로필 사진 공개 여부, 규칙 U-13, 2026-11-02 결정)

ALTER TABLE users ADD COLUMN profile_image_public BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '[v7.2] 프로필 사진 공개 여부(U-13). FALSE면 본인만 볼 수 있고 임원·Super Admin을 포함한 다른 모든 사용자에게 가는 응답의 profileImageUrl은 NULL. 기본 공개'
    AFTER profile_image_url;
