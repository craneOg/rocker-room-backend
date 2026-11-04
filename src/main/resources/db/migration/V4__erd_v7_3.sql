-- ERD v7.3: 탈퇴 계정 "새로 시작"(API 133)이 카카오 프로필로 새 사용자를 만들 수 있도록
-- 로그인 교환·복구 선택 토큰이 카카오 닉네임·이미지를 잠깐 들고 간다. 해당 토큰이 소비되면 값은 NULL로 지운다.
ALTER TABLE one_time_token
    ADD COLUMN profile_name      VARCHAR(50)  NULL
        COMMENT '[v7.3] 카카오 닉네임(탈퇴 후 90일 이내 계정의 LOGIN_EXCHANGE·ACCOUNT_RESTORATION에서만). 소비되면 NULL로 지움'
        AFTER return_to,
    ADD COLUMN profile_image_url VARCHAR(500) NULL
        COMMENT '[v7.3] 카카오 프로필 이미지 URL. profile_name과 같은 조건·수명'
        AFTER profile_name;
