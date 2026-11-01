-- =====================================================================
-- 밴드 동아리 공연·합주 준비 서비스 — ERD v7 (MySQL 8.4 LTS 기준, 8.0.16+ 호환 문법)
-- 2026-10-28 결정 사항 반영
--   v2 → v3: 정합성 보강(공연·동아리 경계 복합 FK, 곡 팀 컬럼 분리, 파생 값 제거, 팀장 FK)
--   v3 → v4: 초대 링크 제거(club_invitation 삭제), 가입 경로 3종 지원
--            (A 가입 코드 / B 동아리 검색 / C 임원 직접 추가 + 본인 수락)
--   v4 → v5: 오디션 심사 기록(audition_entry), 가능 시간 30분 슬롯(available_time → available_slot),
--            users.last_active_at·handle 형식 CHECK, membership.invited_at, part.is_active,
--            song.decision_note·세트리스트 순서 UNIQUE, 팀 이름 공연 내 UNIQUE
--   v5 → v6: 화면 명세 결정(UI-5·7·11·31·34·35)과 10-13 회의·10-14 결정 반영
--            동아리 이름 고유(club.name_key), 프리셋 6종, 팀 신청 정책, 가불·투표 결과 공개 설정,
--            후보곡 참고 링크·메모·철회 구분, Refresh Token(DB 원본 + 로테이션), Access Token 블랙리스트,
--            탈퇴 후 90일 보관(user_withdrawal_archive, auth_identity 90일 유지)
--   v6 → v7: 1회용 토큰 DB 저장(one_time_token: 로그인 교환·복구 선택), 동아리 프로필 사진·대표 색상,
--            가입 코드 형식을 API 개정안 2.1에 맞춤(10자 [A-Z2-9]), 참고 링크 https만(주석)
--   서비스 검증 규칙은 별도 문서(서비스 검증 규칙 v1) 참고. 시간대는 KST 고정(저장은 UTC)
--
-- 공통 규칙
--  * InnoDB / utf8mb4, PK는 BIGINT AUTO_INCREMENT
--  * 시각은 DATETIME(6) + UTC 저장 (JDBC connectionTimeZone=UTC 전제)
--  * 상태·유형 값은 VARCHAR + 애플리케이션 enum (대문자). 값 목록 CHECK는 두지 않음
--    → 값 추가 시 DDL 변경 불필요. 대신 값 검증은 애플리케이션 책임
--  * 불변식(시작<종료, 점수 범위 등)만 CHECK로 DB에서 강제
--  * 삭제 대신 상태 전이(soft) 원칙 → FK는 모두 기본값(RESTRICT), 참조 동작 미지정
--  * 여러 관리자가 동시에 고치는 테이블에는 낙관적 락용 version 컬럼
--  * [v6] Refresh Token은 DB가 원본(refresh_token, 해시 저장), 애플리케이션은 Caffeine을 조회 캐시로만 사용.
--    로그아웃·강제 로그아웃된 Access Token은 만료 전까지 access_token_blacklist로 거부. users.token_version은 모든 요청에서 비교
--  * 파생 값은 저장하지 않음(조회 시 계산). 예외: users.last_active_at(감사 로그 보관 기간과 무관하게 유지해야 하는 운영 메타데이터)
--
-- 경계 강제 규칙 (v3)
--  * 공연 경계: 공연 하위 데이터는 performance_id를 자식에 전파하고,
--    부모의 (id, performance_id) UNIQUE를 가리키는 복합 FK로 "같은 공연"을 DB가 보장
--  * 동아리 경계: performance_member에 club_id를 전파해 "공연의 동아리 = 소속의 동아리"를 보장
--  * 복합 FK 일부가 NULL이면 MySQL은 검사를 생략(MATCH SIMPLE) → 선택 관계에도 적용 가능
--  * 전파 컬럼(performance_id, club_id)은 생성 후 변경하지 않음
--  * DB로 강제하지 않는 경계(서비스 검증): part의 동아리, song.proposed_by_membership_id의 동아리
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 계정
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(50)  NOT NULL COMMENT '서비스 표시 이름(카카오 닉네임 초기값, 중복 가능). 이메일은 수집하지 않음(메일 알림 미사용). [v6] 탈퇴 시 즉시 "탈퇴한 사용자"(원래 값은 user_withdrawal_archive)',
    handle            VARCHAR(30)  NULL
        COMMENT '[v4] 사용자 고유 식별자(임원 직접 추가 시 검색용, 완전 일치 검색만 허용). 최초 로그인 온보딩에서 설정. NULL이면 직접 추가 검색 대상 아님. [v5] 소문자·숫자·밑줄 5~30자(CHECK). 탈퇴 시 NULL로 즉시 해제(다른 사람이 사용 가능). [v6] 탈퇴 복구 시에도 되돌리지 않음(새로 설정)',
    profile_image_url VARCHAR(500) NULL     COMMENT '프로필 이미지 링크(UI-3: 최초 카카오 프로필, 직접 업로드 2MB jpg·jpeg·png·webp, NULL이면 이름 첫 글자 아바타). [v6] 탈퇴 시 NULL(원래 값은 user_withdrawal_archive)',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / WITHDRAWN(서비스 탈퇴). [v6] 90일 안에 복구하면 같은 행을 ACTIVE로 되돌림(U-10)',
    is_super_admin    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '서비스 운영자가 직접 부여',
    token_version     INT          NOT NULL DEFAULT 0 COMMENT '강제 로그아웃·모든 기기 로그아웃·탈퇴·복구 시 +1. 모든 요청에서 Access Token의 버전과 비교(G-02)',
    last_active_at    DATETIME(6)  NULL
        COMMENT '[v5] 마지막 활동 시각. 카카오 로그인·토큰 재발급 시 갱신하되 하루 1회만 기록(UPDATE ... WHERE last_active_at < 오늘 0시 KST). 장기 미접속 관리자 판단용',
    withdrawn_at      DATETIME(6)  NULL     COMMENT '서비스 탈퇴 시각. [v6] 복구 시 NULL. 보관 데이터 파기 기준은 user_withdrawal_archive.withdrawn_at',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_handle (handle) COMMENT '[v4] NULL은 중복 허용',
    KEY idx_users_status (status),
    CONSTRAINT ck_users_handle_format CHECK (handle IS NULL OR REGEXP_LIKE(handle, '^[a-z0-9_]{5,30}$', 'c'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '서비스 사용자 계정';

CREATE TABLE auth_identity (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    provider     VARCHAR(20)  NOT NULL COMMENT '현재 KAKAO만 사용. 제공자 추가 대비로 테이블 분리 유지',
    provider_uid VARCHAR(255) NOT NULL COMMENT '제공자가 발급한 고유 식별자. 계정 복구 시 Super Admin이 새 값으로 교체(audit_log 기록). [v6] 서비스 탈퇴 시 삭제하지 않고 90일 보관(재로그인 대조용, U-10). 파기 배치(U-11) 또는 "새로 시작" 선택 시 삭제. 카카오 연결 끊기 후에도 같은 앱의 회원번호는 유지됨',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_identity_provider_uid (provider, provider_uid),
    UNIQUE KEY uk_auth_identity_user_provider (user_id, provider) COMMENT '사용자당 제공자별 계정 1개',
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '소셜 로그인 식별 정보';

-- [v6] 탈퇴 후 90일 보관. users에는 익명 값만 남기고 원래 프로필은 이 테이블에만 둔다(조회 실수로 인한 노출 방지).
--      복구(U-10) 또는 파기(U-11, "새로 시작") 시 행 삭제. 소속·역할·동아리별 표시 이름은 보관·복구 대상 아님
CREATE TABLE user_withdrawal_archive (
    user_id           BIGINT       NOT NULL,
    name              VARCHAR(50)  NOT NULL COMMENT '탈퇴 직전 users.name',
    profile_image_url VARCHAR(500) NULL     COMMENT '탈퇴 직전 users.profile_image_url',
    withdrawn_at      DATETIME(6)  NOT NULL COMMENT '파기 기준 시각. withdrawn_at + 90일이 지나면 배치로 파기(U-11)',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    KEY idx_user_withdrawal_archive_withdrawn (withdrawn_at) COMMENT '파기 배치 조회용',
    CONSTRAINT fk_user_withdrawal_archive_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '[v6] 탈퇴 계정 프로필 보관(90일). 복구 대상은 이름·사진뿐';

-- [v6] Refresh Token. 원본은 DB, 애플리케이션은 Caffeine을 캐시로만 사용(서버 재시작 후에도 로그인 유지)
--      로테이션: 재발급마다 새 행을 만들고 이전 행은 revoked_at·replaced_by_id 기록.
--      이미 교체된 토큰이 다시 오면 탈취로 보고 같은 family_id의 토큰을 모두 폐기(REUSE_DETECTED)
CREATE TABLE refresh_token (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    family_id      CHAR(36)     NOT NULL COMMENT '로그인 1회(기기 1개)에서 이어지는 토큰 묶음 UUID. 로테이션해도 유지',
    token_hash     CHAR(64)     NOT NULL COMMENT '토큰 값의 SHA-256 16진수. 평문 저장 금지',
    expires_at     DATETIME(6)  NOT NULL COMMENT '만료 시각 = min(발급 + 30일, family_expires_at). 재발급할 때마다 30일 갱신(슬라이딩)',
    family_expires_at DATETIME(6) NOT NULL COMMENT '로그인 묶음 절대 만료 = 최초 로그인 + 90일. 로테이션해도 그대로 복사',
    revoked_at     DATETIME(6)  NULL     COMMENT 'NULL이면 유효(만료 전까지)',
    revoke_reason  VARCHAR(20)  NULL     COMMENT 'ROTATED(재발급으로 교체) / LOGOUT(현재 기기) / LOGOUT_ALL(모든 기기) / FORCE_LOGOUT(Super Admin) / REUSE_DETECTED(탈취 의심) / WITHDRAWN(탈퇴)',
    replaced_by_id BIGINT       NULL     COMMENT '로테이션으로 새로 발급된 토큰 행',
    created_ip     VARCHAR(45)  NULL     COMMENT '발급 요청 IP(IPv6 포함). 기록용이며 차단 기준으로 쓰지 않음',
    user_agent     VARCHAR(255) NULL     COMMENT '기기 구분 표시용',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id, revoked_at),
    KEY idx_refresh_token_family (family_id),
    KEY idx_refresh_token_expires (expires_at) COMMENT '만료 행 정리 배치용',
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_token_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id),
    CONSTRAINT ck_refresh_token_revoke CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '[v6] Refresh Token 원본(해시). expires_at + 7일이 지나면 배치로 물리 삭제';

-- [v6] 로그아웃 등으로 만료 전에 무효화한 Access Token(jti). 만료 시각이 지나면 배치로 물리 삭제
--      전체 무효화(모든 기기·강제 로그아웃·탈퇴)는 users.token_version으로 처리하므로 여기에 넣지 않음
CREATE TABLE access_token_blacklist (
    jti        CHAR(36)    NOT NULL COMMENT 'Access Token의 jti 클레임(UUID)',
    user_id    BIGINT      NOT NULL,
    expires_at DATETIME(6) NOT NULL COMMENT '원래 토큰 만료 시각. 이후에는 행이 필요 없음',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (jti),
    KEY idx_access_token_blacklist_expires (expires_at),
    CONSTRAINT fk_access_token_blacklist_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '[v6] Access Token 블랙리스트(현재 기기 로그아웃 등)';

-- [v7] 1회용 토큰(로그인 교환 60초, 복구 선택 10분). 원문은 HttpOnly 쿠키에만 두고 DB에는 SHA-256 해시만 저장.
--      소비: UPDATE one_time_token SET used_at = NOW(6)
--            WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > NOW(6)
--            → 영향 행 1개일 때만 성공(인스턴스 수와 무관하게 원자적 1회 사용)
--      정리: expires_at + 1일이 지난 행을 하루 1회 배치로 물리 삭제. 탈퇴·파기 시 해당 사용자의 미사용 토큰 삭제
CREATE TABLE one_time_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  BINARY(32)   NOT NULL COMMENT '토큰 원문의 SHA-256(32바이트). 평문 저장 금지',
    purpose     VARCHAR(30)  NOT NULL COMMENT 'LOGIN_EXCHANGE(AUTH-02→03, 60초) / ACCOUNT_RESTORATION(AUTH-03→13, 10분). OAUTH_STATE 포함 여부는 미결',
    user_id     BIGINT       NULL     COMMENT '토큰 대상 사용자. OAUTH_STATE처럼 사용자 없는 용도는 NULL',
    return_to   VARCHAR(200) NULL     COMMENT '로그인 후 돌아갈 FE 상대 경로(OAUTH_STATE일 때만)',
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL     COMMENT 'NULL이면 미사용. 1회 소비 시각',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_one_time_token_hash (token_hash),
    KEY idx_one_time_token_expires (expires_at) COMMENT '정리 배치용',
    KEY idx_one_time_token_user (user_id),
    CONSTRAINT fk_one_time_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '[v7] 1회용 토큰(로그인 교환·복구 선택) 해시';

-- ---------------------------------------------------------------------
-- 2. 동아리(테넌트)와 소속
-- ---------------------------------------------------------------------
CREATE TABLE club (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    name_key    VARCHAR(100) AS (LOWER(REGEXP_REPLACE(name, '[[:space:]]+', ''))) STORED
        COMMENT '[v6] 비교용 이름(모든 공백 제거 + 소문자). 서비스 전체 고유(C-15, UI-31). 비활성 동아리 포함',
    description VARCHAR(500) NULL,
    club_type   VARCHAR(20)  NOT NULL DEFAULT 'CLUB' COMMENT 'CLUB(동아리형) / BAND(단일 팀 밴드). 온보딩·화면 구성용이며 권한·구조는 동일',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE(Super Admin 비활성: 변경 기능 제한). 사유는 audit_log',
    join_code   CHAR(10)     NULL
        COMMENT '[v4] 가입 코드(경로 A). NULL = 코드 가입 미사용. 재발급 = 값 교체(이전 코드 즉시 무효, audit_log 기록). 화면 재표시를 위해 평문 저장(가입은 임원 승인 필요). [v7] 서버 발급 10자 A-Z2-9. 생성 시 NULL',
    profile_image_url VARCHAR(500) NULL
        COMMENT '[v7] 동아리 프로필 사진 링크. NULL이면 기본 이미지',
    theme_color CHAR(7)      NULL
        COMMENT '[v7] 동아리 대표 색상 #RRGGBB(대문자 16진수, CHECK). NULL이면 기본 색상',
    is_searchable BOOLEAN    NOT NULL DEFAULT FALSE
        COMMENT '[v4] 동아리 검색 노출 여부(경로 B). 기본 비공개',
    created_by  BIGINT       NOT NULL COMMENT '생성자 = 최초 Club Admin',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_club_join_code (join_code) COMMENT '[v4] NULL은 중복 허용',
    UNIQUE KEY uk_club_name_key (name_key) COMMENT '[v6] 동아리 이름 고유(대소문자·공백 무시)',
    KEY idx_club_searchable_name (is_searchable, name) COMMENT '[v4] 공개 동아리 이름 검색',
    CONSTRAINT fk_club_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_club_join_code_format CHECK (join_code IS NULL OR REGEXP_LIKE(join_code, '^[A-Z2-9]{10}$', 'c')),
    CONSTRAINT ck_club_theme_color_format CHECK (theme_color IS NULL OR REGEXP_LIKE(theme_color, '^#[0-9A-F]{6}$', 'c'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '동아리·밴드(멀티테넌시의 테넌트)';

CREATE TABLE membership (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    club_id       BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    display_name  VARCHAR(50) NULL     COMMENT '동아리 내 표시 이름. NULL이면 users.name 사용. [v6] 서비스 탈퇴 시 NULL, 복구하지 않음',
    member_type   VARCHAR(20) NOT NULL DEFAULT 'REGULAR' COMMENT 'REGULAR(부원) / GUEST(외부 객원: 자신이 참여한 공연만 열람)',
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'CLUB_ADMIN / MEMBER. [v5] 소속 해제·가입 미확정 상태에서는 MEMBER만(CHECK). 최소 1명 계산은 ACTIVE·ON_LEAVE 관리자만',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING(본인 가입 신청, 임원 승인 대기) / INVITED(임원 직접 추가, 본인 수락 대기) / REJECTED(임원 거절) / DECLINED(본인 거절) / ACTIVE(활동) / ON_LEAVE(휴학: 활동과 동일 권한) / GRADUATED(졸업: 본인 공연 신청만 불가) / WITHDRAWN(탈퇴·소속 해제: 접근 불가)',
    join_source   VARCHAR(20) NOT NULL
        COMMENT '[v4] 소속 생성 경로: CLUB_CREATOR(동아리 생성자) / JOIN_CODE(경로 A) / SEARCH(경로 B) / ADMIN_ADDED(경로 C). 재가입 시 새 경로로 갱신',
    approved_at   DATETIME(6) NULL     COMMENT '소속 확정 시각(임원 승인 또는 본인 수락)',
    invited_at    DATETIME(6) NULL
        COMMENT '[v5] 임원 직접 추가(INVITED) 시각. 유효기간 1개월(만료 시각은 저장하지 않고 조회 시 계산). 재초대 시 갱신',
    left_at       DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_club_user (club_id, user_id) COMMENT '재가입·재초대는 같은 행을 PENDING/INVITED로 되돌림. 이력은 audit_log',
    UNIQUE KEY uk_membership_id_club (id, club_id) COMMENT '[v3] 동아리 경계 복합 FK 대상',
    KEY idx_membership_user (user_id),
    CONSTRAINT fk_membership_club FOREIGN KEY (club_id) REFERENCES club (id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_membership_guest_not_admin CHECK (NOT (member_type = 'GUEST' AND role = 'CLUB_ADMIN')),
    CONSTRAINT ck_membership_admin_status CHECK (NOT (role = 'CLUB_ADMIN' AND status IN ('PENDING', 'INVITED', 'REJECTED', 'DECLINED', 'WITHDRAWN'))),
    CONSTRAINT ck_membership_invited_at CHECK (status <> 'INVITED' OR invited_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '사용자-동아리 소속(다대다). 역할·상태는 동아리별';

CREATE TABLE part (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    club_id       BIGINT      NOT NULL,
    name          VARCHAR(50) NOT NULL COMMENT '보컬, 기타, 베이스, 드럼, 키보드 등. 동아리 생성 시 기본값 시드',
    display_order INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE
        COMMENT '[v5] FALSE = 새 선택 목록에서 숨김(과거 기록은 유지·표시). 참조가 있어 삭제할 수 없는 파트의 정리용',
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_club_name (club_id, name),
    CONSTRAINT fk_part_club FOREIGN KEY (club_id) REFERENCES club (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '동아리별 파트 목록. 참조하는 행의 동아리와 일치하는지는 서비스 검증';

CREATE TABLE membership_part (
    membership_id BIGINT NOT NULL,
    part_id       BIGINT NOT NULL,
    PRIMARY KEY (membership_id, part_id),
    KEY idx_membership_part_part (part_id),
    CONSTRAINT fk_membership_part_membership FOREIGN KEY (membership_id) REFERENCES membership (id),
    CONSTRAINT fk_membership_part_part FOREIGN KEY (part_id) REFERENCES part (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '부원이 동아리에서 다룰 수 있는 파트(같은 동아리 파트인지는 서비스 검증)';

-- ---------------------------------------------------------------------
-- 3. 공연과 운영 정책
-- ---------------------------------------------------------------------
CREATE TABLE performance (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    club_id            BIGINT       NOT NULL,
    title              VARCHAR(200) NOT NULL,
    perform_date       DATE         NULL     COMMENT '예정 일자. 초안 단계에서는 비워 둘 수 있음',
    location           VARCHAR(200) NULL,
    preset             VARCHAR(20)  NOT NULL
        COMMENT '[v6] TEAM_FIRST(팀 먼저) / SONG_FIRST(곡 먼저) / SONG_FIXED(곡 지정형) / SELF_TEAM(자율 팀 신청형) / BAND(단일 밴드) / CUSTOM(직접 설정). 생성 시 필수 선택(기본값 없음, UI-5). 시작 흐름 안내 + 정책 기본값용이며 동작을 제한하지 않음',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / ACTIVE / LOCKED / FINISHED / CANCELED',
    created_by_user_id BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_performance_id_club (id, club_id) COMMENT '[v3] 동아리 경계 복합 FK 대상',
    KEY idx_performance_club_status (club_id, status),
    CONSTRAINT fk_performance_club FOREIGN KEY (club_id) REFERENCES club (id),
    CONSTRAINT fk_performance_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연(준비 단위)';

-- 기능별 *_state: DISABLED(사용 안 함) / CLOSED(사용하나 현재 닫힘) / OPEN(입력 받는 중)
-- *_deadline: 지정 시 해당 시각 이후 입력 거부(자동 마감). NULL이면 수동으로 닫을 때까지 열림
CREATE TABLE performance_policy (
    performance_id             BIGINT      NOT NULL,
    application_state          VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '공연 참여 신청',
    application_deadline       DATETIME(6) NULL,
    candidate_state            VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '후보곡 신청. DISABLED면 Club Admin만 후보곡 등록',
    candidate_deadline         DATETIME(6) NULL,
    candidate_scope            VARCHAR(20) NOT NULL DEFAULT 'ALL_PARTICIPANTS'
        COMMENT 'ALL_PARTICIPANTS(공연 범위 후보 허용) / TEAM_MEMBERS(팀 범위 후보만 허용 → song.candidate_team_id 필수, 서비스 검증)',
    candidate_limit_per_member INT         NULL     COMMENT '1인 후보곡 신청 수. NULL = 제한 없음',
    playability_state          VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '가불(가능/불가능/보류) 표기',
    playability_deadline       DATETIME(6) NULL,
    vote_state                 VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '일반 투표',
    vote_deadline              DATETIME(6) NULL,
    vote_method                VARCHAR(10) NOT NULL DEFAULT 'APPROVAL'
        COMMENT 'APPROVAL(찬성) / SCORE(1~5점). 변경 가능. 투표가 있는 상태에서 변경하면 해당 공연의 기존 투표 전체 삭제(서비스 처리, audit_log 기록)',
    vote_max_choices           INT         NULL     COMMENT 'APPROVAL일 때 1인 최대 선택 곡 수. NULL = 제한 없음',
    playability_result_public  BOOLEAN     NOT NULL DEFAULT TRUE
        COMMENT '[v6] 가불 응답자 이름 공개. FALSE면 부원에게 집계 숫자만(임원은 항상 전체, V-09, UI-35)',
    vote_result_public         BOOLEAN     NOT NULL DEFAULT TRUE
        COMMENT '[v6] 투표 응답자 이름 공개. 가불과 따로 설정(V-09, UI-35)',
    team_submission_state      VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '[v6] 부원 팀 신청(UI-7). T-02의 "정책상 허용" 판단',
    team_submission_deadline   DATETIME(6) NULL     COMMENT '[v6]',
    availability_state         VARCHAR(10) NOT NULL DEFAULT 'DISABLED' COMMENT '합주 가능 시간 수집',
    availability_deadline      DATETIME(6) NULL,
    practice_season_start      DATE        NULL     COMMENT '합주 시즌 시작(가능 시간 입력 허용 범위)',
    practice_season_end        DATE        NULL,
    max_songs_per_member       INT         NULL     COMMENT '1인 권장 출연 곡 수. 초과 시 경고만(차단 없음)',
    updated_at                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (performance_id),
    CONSTRAINT fk_performance_policy_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT ck_policy_candidate_limit CHECK (candidate_limit_per_member IS NULL OR candidate_limit_per_member > 0),
    CONSTRAINT ck_policy_vote_max_choices CHECK (vote_max_choices IS NULL OR vote_max_choices > 0),
    CONSTRAINT ck_policy_max_songs CHECK (max_songs_per_member IS NULL OR max_songs_per_member > 0),
    CONSTRAINT ck_policy_season CHECK (practice_season_start IS NULL OR practice_season_end IS NULL
                                       OR practice_season_start <= practice_season_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연별 운영 정책(1:1). 프리셋 선택 시 기본값 스냅샷';

CREATE TABLE performance_member (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    performance_id BIGINT      NOT NULL,
    club_id        BIGINT      NOT NULL COMMENT '[v3] 동아리 경계 전파 컬럼. 공연의 동아리와 소속의 동아리가 같아야 함',
    membership_id  BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED(참여 희망자) / APPROVED / REJECTED / WITHDRAWN',
    source         VARCHAR(20) NOT NULL COMMENT 'APPLICATION(본인 신청) / ADMIN(임원 지정) / TEAM_SUBMISSION(팀 제출에 포함)',
    decided_at     DATETIME(6) NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_performance_member (performance_id, membership_id),
    UNIQUE KEY uk_performance_member_id_performance (id, performance_id) COMMENT '[v3] 공연 경계 복합 FK 대상',
    KEY idx_performance_member_membership (membership_id, club_id),
    CONSTRAINT fk_performance_member_performance FOREIGN KEY (performance_id, club_id) REFERENCES performance (id, club_id),
    CONSTRAINT fk_performance_member_membership FOREIGN KEY (membership_id, club_id) REFERENCES membership (id, club_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연 참여자. 희망자(APPLIED)도 후보곡 제출·가불 가능';

-- ---------------------------------------------------------------------
-- 4. 팀
-- ---------------------------------------------------------------------
CREATE TABLE team (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    performance_id     BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE
        COMMENT '단일 팀 밴드(club_type = BAND)에서 공연 생성 시 자동 생성되는 기본 팀. 공연당 1개(서비스 검증, 교체 시 기존 해제를 먼저 처리). 보조 팀은 FALSE',
    leader_member_id   BIGINT       NULL
        COMMENT '팀장(performance_member). 제출자가 기본값. [v3] 이 팀의 팀원이어야 함(fk_team_leader_is_member로 DB 강제)',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT / SUBMITTED / APPROVED / REJECTED / WITHDRAWN. 임원이 만든 팀은 바로 APPROVED',
    source             VARCHAR(20)  NOT NULL COMMENT 'ADMIN(임원 편성) / SELF(부원 제출) / FROM_SESSIONS(곡 연주자로 팀 생성)',
    created_by_user_id BIGINT       NOT NULL,
    submitted_at       DATETIME(6)  NULL,
    decided_at         DATETIME(6)  NULL,
    reject_reason      VARCHAR(500) NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_id_performance (id, performance_id) COMMENT '[v3] 공연 경계 복합 FK 대상',
    UNIQUE KEY uk_team_performance_name (performance_id, name) COMMENT '[v5] 공연 내 팀 이름 중복 금지(대소문자 구분 없음)',
    KEY idx_team_performance_status (performance_id, status),
    CONSTRAINT fk_team_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_team_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
    -- fk_team_leader_is_member는 team_member 생성 후 ALTER로 추가(순환 참조)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연 단위 팀. 한 팀은 여러 확정 곡 담당';

CREATE TABLE team_member (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    team_id               BIGINT      NOT NULL,
    performance_id        BIGINT      NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼',
    performance_member_id BIGINT      NOT NULL,
    part_id               BIGINT      NULL     COMMENT '팀에서의 대표 파트(팀 단위 파트 충족 확인용). 곡별 파트는 song_session_member',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member (team_id, performance_member_id) COMMENT '팀장 복합 FK 대상 겸용',
    KEY idx_team_member_member (performance_member_id, performance_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id, performance_id) REFERENCES team (id, performance_id),
    CONSTRAINT fk_team_member_member FOREIGN KEY (performance_member_id, performance_id) REFERENCES performance_member (id, performance_id),
    CONSTRAINT fk_team_member_part FOREIGN KEY (part_id) REFERENCES part (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '팀 전체 구성원';

-- [v3] 팀장은 이 팀의 팀원이어야 함. leader_member_id가 NULL이면 검사 생략.
-- 팀장인 팀원 행은 삭제 불가(RESTRICT) → 팀장 교체 후 삭제
ALTER TABLE team
    ADD CONSTRAINT fk_team_leader_is_member
        FOREIGN KEY (id, leader_member_id) REFERENCES team_member (team_id, performance_member_id);

-- ---------------------------------------------------------------------
-- 5. 곡(후보곡 = 확정 곡 = 세트리스트)
-- ---------------------------------------------------------------------
CREATE TABLE song (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    performance_id            BIGINT       NOT NULL,
    candidate_team_id         BIGINT       NULL
        COMMENT '[v3] 후보 범위. 값 있으면 팀 범위 후보(투표·가불 대상 = 팀원), NULL이면 공연 범위 후보. 생성 후 변경하지 않음',
    assigned_team_id          BIGINT       NULL
        COMMENT '[v3] 담당 팀. CONFIRMED일 때만 값 보유(CHECK). 팀 범위 후보 확정 시 기본값 = candidate_team_id. LOCKED 전까지 배정 필요(서비스 검증)',
    title                     VARCHAR(200) NOT NULL,
    artist                    VARCHAR(200) NULL,
    reference_url             VARCHAR(500) NULL     COMMENT '[v6] 참고 링크(UI-11). [v7] https://만 허용(API 개정안 2.1, 검증 규칙 10-27). 서버가 자동으로 가져오지 않음(SSRF 방지)',
    memo                      VARCHAR(500) NULL     COMMENT '[v6] 제안 메모(UI-11). 제안 후 제안자는 수정 불가, 임원만 수정(S-11)',
    status                    VARCHAR(20)  NOT NULL DEFAULT 'PROPOSED'
        COMMENT 'PROPOSED / CONFIRMED(공연곡 선정) / REJECTED / WITHDRAWN. [v5] 세트리스트 배치 여부는 setlist_order IS NOT NULL로 판별(별도 상태 없음). 직접 확정은 CONFIRMED로 생성. 전이: PROPOSED→CONFIRMED·REJECTED·WITHDRAWN(철회는 PROPOSED에서만), CONFIRMED→PROPOSED(확정 취소)·REJECTED(경고), REJECTED→PROPOSED. CONFIRMED에서 벗어나면 assigned_team_id·setlist_order 해제. [v6] 임원 삭제(PROPOSED에서만, S-11)도 WITHDRAWN',
    withdrawal_type           VARCHAR(20)  NULL
        COMMENT '[v6] WITHDRAWN일 때만 값 보유(CHECK): PROPOSER(제안자 철회) / ADMIN(임원 삭제, "임원이 삭제한 곡이에요") / TEAM_WITHDRAWN(범위 팀 철회·삭제에 따른 자동 철회, UI-10·R-5). 모든 WITHDRAWN은 1인 제안 한도에서 제외(S-03)',
    proposed_by_membership_id BIGINT       NOT NULL COMMENT '제안자 또는 직접 등록한 임원(같은 동아리인지는 서비스 검증)',
    setlist_order             INT          NULL     COMMENT '공연 전체 기준 순서. CONFIRMED일 때만 값 보유(CHECK). [v5] 공연 내 UNIQUE, 변경은 전체 순서 일괄 저장(한 트랜잭션에서 NULL로 비운 뒤 재부여). LOCKED 진입 시 1부터 재부여',
    decision_note             VARCHAR(500) NULL     COMMENT '[v5] 확정·탈락·확정 취소 등 결정 사유(최근 결정 기준). 이력은 audit_log',
    decided_at                DATETIME(6)  NULL,
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_song_id_performance (id, performance_id) COMMENT '[v3] 공연 경계 복합 FK 대상',
    UNIQUE KEY uk_song_setlist_order (performance_id, setlist_order) COMMENT '[v5] 세트리스트 순서 중복 금지(NULL은 중복 허용)',
    KEY idx_song_performance_status (performance_id, status, setlist_order),
    KEY idx_song_candidate_team (candidate_team_id, performance_id),
    KEY idx_song_assigned_team (assigned_team_id, performance_id),
    CONSTRAINT fk_song_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_song_candidate_team FOREIGN KEY (candidate_team_id, performance_id) REFERENCES team (id, performance_id),
    CONSTRAINT fk_song_assigned_team FOREIGN KEY (assigned_team_id, performance_id) REFERENCES team (id, performance_id),
    CONSTRAINT fk_song_proposed_by FOREIGN KEY (proposed_by_membership_id) REFERENCES membership (id),
    CONSTRAINT ck_song_setlist_order CHECK (status = 'CONFIRMED' OR setlist_order IS NULL),
    CONSTRAINT ck_song_assigned_team CHECK (status = 'CONFIRMED' OR assigned_team_id IS NULL),
    CONSTRAINT ck_song_withdrawal_type CHECK ((status = 'WITHDRAWN') = (withdrawal_type IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연 곡(후보곡·선정곡). 세트리스트 = CONFIRMED이면서 setlist_order가 있는 곡';

CREATE TABLE song_part_requirement (
    song_id        BIGINT NOT NULL,
    part_id        BIGINT NOT NULL,
    required_count INT    NOT NULL,
    PRIMARY KEY (song_id, part_id),
    KEY idx_song_part_requirement_part (part_id),
    CONSTRAINT fk_song_part_requirement_song FOREIGN KEY (song_id) REFERENCES song (id),
    CONSTRAINT fk_song_part_requirement_part FOREIGN KEY (part_id) REFERENCES part (id),
    CONSTRAINT ck_song_part_requirement_count CHECK (required_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '곡별 필요 편성(입력 선택). 행이 없으면 편성 미지정';

CREATE TABLE song_session_member (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    song_id               BIGINT      NOT NULL,
    performance_id        BIGINT      NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼',
    performance_member_id BIGINT      NOT NULL,
    part_id               BIGINT      NOT NULL,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_song_session_member (song_id, performance_member_id, part_id),
    KEY idx_song_session_member_song (song_id, performance_id),
    KEY idx_song_session_member_member (performance_member_id, performance_id),
    CONSTRAINT fk_song_session_song FOREIGN KEY (song_id, performance_id) REFERENCES song (id, performance_id),
    CONSTRAINT fk_song_session_member FOREIGN KEY (performance_member_id, performance_id) REFERENCES performance_member (id, performance_id),
    CONSTRAINT fk_song_session_part FOREIGN KEY (part_id) REFERENCES part (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '곡별 실제 연주자. 모든 확정 곡에 명시. [v3] 객원 여부는 저장하지 않고 조회 시 계산(담당 팀의 team_member가 아니면 객원)';

CREATE TABLE song_playability (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    song_id               BIGINT       NOT NULL,
    performance_id        BIGINT       NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼',
    performance_member_id BIGINT       NOT NULL COMMENT '응답자',
    part_id               BIGINT       NOT NULL COMMENT '응답 파트(단일 파트 부원은 자동 선택)',
    answer                VARCHAR(20)  NOT NULL COMMENT 'POSSIBLE / IMPOSSIBLE / UNDECIDED(보류)',
    reason                VARCHAR(500) NULL     COMMENT '불가능·보류 사유',
    submitted_by_user_id  BIGINT       NOT NULL COMMENT '실제 입력자(Club Admin 대리 입력 추적)',
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_song_playability (song_id, performance_member_id, part_id) COMMENT '최신 결과 1건만 유지(upsert)',
    KEY idx_song_playability_song (song_id, performance_id),
    KEY idx_song_playability_member (performance_member_id, performance_id),
    CONSTRAINT fk_song_playability_song FOREIGN KEY (song_id, performance_id) REFERENCES song (id, performance_id),
    CONSTRAINT fk_song_playability_member FOREIGN KEY (performance_member_id, performance_id) REFERENCES performance_member (id, performance_id),
    CONSTRAINT fk_song_playability_part FOREIGN KEY (part_id) REFERENCES part (id),
    CONSTRAINT fk_song_playability_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '가불 표기(파트 단위)';

CREATE TABLE song_vote (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    song_id               BIGINT      NOT NULL,
    performance_id        BIGINT      NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼. 투표 방식 변경 시 공연 단위 일괄 삭제에도 사용',
    performance_member_id BIGINT      NOT NULL,
    score                 TINYINT     NOT NULL COMMENT 'APPROVAL = 1 고정, SCORE = 1~5. 찬성 취소는 행 삭제',
    submitted_by_user_id  BIGINT      NOT NULL COMMENT '실제 입력자(Club Admin 대리 입력 추적)',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_song_vote (song_id, performance_member_id),
    KEY idx_song_vote_song (song_id, performance_id),
    KEY idx_song_vote_member (performance_member_id, performance_id),
    KEY idx_song_vote_performance (performance_id),
    CONSTRAINT fk_song_vote_song FOREIGN KEY (song_id, performance_id) REFERENCES song (id, performance_id),
    CONSTRAINT fk_song_vote_member FOREIGN KEY (performance_member_id, performance_id) REFERENCES performance_member (id, performance_id),
    CONSTRAINT fk_song_vote_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_song_vote_score CHECK (score BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '일반 투표(가불과 분리 저장)';

-- ---------------------------------------------------------------------
-- 6. 가능 시간과 합주 일정
-- ---------------------------------------------------------------------
CREATE TABLE available_slot (
    performance_member_id BIGINT      NOT NULL COMMENT '승인된 참여자 본인만 입력(대리 입력 불가, 서비스 검증)',
    slot_start            DATETIME(6) NOT NULL COMMENT '[v5] 30분 슬롯 시작 시각(UTC 저장, KST 기준 30분 정렬). 슬롯 = [slot_start, slot_start + 30분)',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (performance_member_id, slot_start),
    KEY idx_available_slot_start (slot_start),
    CONSTRAINT fk_available_slot_member FOREIGN KEY (performance_member_id) REFERENCES performance_member (id),
    CONSTRAINT ck_available_slot_align CHECK (MINUTE(slot_start) IN (0, 30) AND SECOND(slot_start) = 0 AND MICROSECOND(slot_start) = 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '[v5] 합주 가능 시간(30분 슬롯 1개당 1행, available_time 대체). 저장은 날짜·기간 단위 일괄 교체, 공통 시간은 슬롯별 COUNT로 계산';

CREATE TABLE practice_schedule (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    performance_id       BIGINT       NOT NULL,
    schedule_type        VARCHAR(20)  NOT NULL DEFAULT 'PRACTICE' COMMENT 'PRACTICE(합주) / REHEARSAL(리허설, 평가 없음) / AUDITION(오디션) / ETC. 표시·필터용',
    status               VARCHAR(20)  NOT NULL DEFAULT 'TENTATIVE'
        COMMENT 'TENTATIVE(임시: 임원만 열람) / CONFIRMED(공개) / CANCELED(기록 보존)',
    title                VARCHAR(100) NULL,
    start_at             DATETIME(6)  NOT NULL,
    end_at               DATETIME(6)  NOT NULL,
    location             VARCHAR(200) NULL     COMMENT '장소 직접 입력(합주실 예약 기능 미채택)',
    for_all_participants BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'TRUE면 승인된 공연 참여자 전원이 참석 대상',
    memo                 VARCHAR(500) NULL,
    created_by_user_id   BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_schedule_id_performance (id, performance_id) COMMENT '[v3] 공연 경계 복합 FK 대상',
    KEY idx_practice_schedule_performance_start (performance_id, start_at),
    CONSTRAINT fk_practice_schedule_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_practice_schedule_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_practice_schedule_range CHECK (start_at < end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '합주·리허설 일정. 참석자 = 연결 팀의 팀원 ∪ 연결 곡의 연주자(객원 포함), for_all_participants면 참여자 전원';

CREATE TABLE practice_schedule_team (
    practice_schedule_id BIGINT NOT NULL,
    team_id              BIGINT NOT NULL,
    performance_id       BIGINT NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼',
    PRIMARY KEY (practice_schedule_id, team_id),
    KEY idx_practice_schedule_team_schedule (practice_schedule_id, performance_id),
    KEY idx_practice_schedule_team_team (team_id, performance_id),
    CONSTRAINT fk_practice_schedule_team_schedule FOREIGN KEY (practice_schedule_id, performance_id) REFERENCES practice_schedule (id, performance_id),
    CONSTRAINT fk_practice_schedule_team_team FOREIGN KEY (team_id, performance_id) REFERENCES team (id, performance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '일정-팀 연결(0~N)';

CREATE TABLE practice_schedule_song (
    practice_schedule_id BIGINT NOT NULL,
    song_id              BIGINT NOT NULL,
    performance_id       BIGINT NOT NULL COMMENT '[v3] 공연 경계 전파 컬럼',
    PRIMARY KEY (practice_schedule_id, song_id),
    KEY idx_practice_schedule_song_schedule (practice_schedule_id, performance_id),
    KEY idx_practice_schedule_song_song (song_id, performance_id),
    CONSTRAINT fk_practice_schedule_song_schedule FOREIGN KEY (practice_schedule_id, performance_id) REFERENCES practice_schedule (id, performance_id),
    CONSTRAINT fk_practice_schedule_song_song FOREIGN KEY (song_id, performance_id) REFERENCES song (id, performance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '일정-곡 연결(0~N)';

-- ---------------------------------------------------------------------
-- 7. 오디션 심사 (v5)
-- ---------------------------------------------------------------------
CREATE TABLE audition_entry (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    performance_id        BIGINT       NOT NULL COMMENT '공연 경계 전파 컬럼',
    song_id               BIGINT       NOT NULL COMMENT '심사 대상 곡',
    performance_member_id BIGINT       NULL     COMMENT '세션 심사 지원자. NULL이면 곡 심사(팀·연주자 묶음이 그 곡을 연주)',
    part_id               BIGINT       NULL     COMMENT '세션 심사 지원 파트. performance_member_id와 함께 NULL이거나 함께 값 보유(CHECK)',
    practice_schedule_id  BIGINT       NULL     COMMENT '오디션 일정(schedule_type = AUDITION). 일정 없이도 결과 기록 가능',
    memo                  VARCHAR(500) NULL     COMMENT '등록 메모(지원 동기·곡 해석·참고 링크 등, 등록자 작성)',
    result                VARCHAR(10)  NULL     COMMENT 'NULL(심사 대기) / PASSED / FAILED. 재심은 새 행. 결과가 곡·연주자를 자동 변경하지 않음(FE가 후속 처리 제안)',
    comment               VARCHAR(500) NULL     COMMENT '심사 의견(심사자 작성)',
    reviewed_by_user_id   BIGINT       NULL,
    reviewed_at           DATETIME(6)  NULL,
    created_by_user_id    BIGINT       NOT NULL COMMENT 'MVP는 Club Admin만 등록',
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audition_entry_song (song_id, performance_id),
    KEY idx_audition_entry_member (performance_member_id, performance_id),
    KEY idx_audition_entry_schedule (practice_schedule_id, performance_id),
    KEY idx_audition_entry_performance_result (performance_id, result),
    CONSTRAINT fk_audition_entry_song FOREIGN KEY (song_id, performance_id) REFERENCES song (id, performance_id),
    CONSTRAINT fk_audition_entry_member FOREIGN KEY (performance_member_id, performance_id) REFERENCES performance_member (id, performance_id),
    CONSTRAINT fk_audition_entry_part FOREIGN KEY (part_id) REFERENCES part (id),
    CONSTRAINT fk_audition_entry_schedule FOREIGN KEY (practice_schedule_id, performance_id) REFERENCES practice_schedule (id, performance_id),
    CONSTRAINT fk_audition_entry_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_audition_entry_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_audition_entry_session_pair CHECK ((performance_member_id IS NULL) = (part_id IS NULL)),
    CONSTRAINT ck_audition_entry_review CHECK (
        (result IS NULL AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (result IS NOT NULL AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '오디션 지원·심사 기록. 곡 심사 = 지원자 NULL, 세션 심사 = 지원자·파트 지정. 팀 심사는 team 승인 흐름 사용. 같은 대상의 심사 대기 행은 1개(서비스 검증)';

-- ---------------------------------------------------------------------
-- 8. 감사 기록
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    club_id       BIGINT       NULL     COMMENT '서비스 차원 이벤트(로그인 등)는 NULL',
    actor_user_id BIGINT       NULL     COMMENT '시스템 처리(자동 마감 등)는 NULL',
    action        VARCHAR(50)  NOT NULL COMMENT '예: CLUB_DEACTIVATED, ADMIN_GRANTED, ADMIN_EMERGENCY_REPLACED, ACCOUNT_RELINKED, ACCOUNT_WITHDRAWN, ACCOUNT_RESTORED, ACCOUNT_PURGED, CLUB_REACTIVATED, ADMIN_SUCCESSION, JOIN_CODE_REGENERATED, MEMBERSHIP_APPROVED, MEMBER_ADDED_BY_ADMIN, FORCE_LOGOUT, VOTE_METHOD_CHANGED, SETLIST_CONFIRMED, PERFORMANCE_STATUS_CHANGED, LOCKED_EXCEPTION_EDIT, SONG_DECIDED, AUDITION_REVIEWED',
    target_type   VARCHAR(30)  NOT NULL,
    target_id     BIGINT       NULL,
    reason        VARCHAR(500) NULL,
    detail        JSON         NULL     COMMENT '변경 전후 값 등(ID 기준). 이름 등 개인정보·토큰·카카오 인증 정보는 저장 금지',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_log_club_created (club_id, created_at),
    KEY idx_audit_log_target (target_type, target_id),
    KEY idx_audit_log_actor_created (actor_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '권한·보안·주요 상태 변경 기록. 대상 삭제와 무관하게 남도록 FK를 두지 않음';
