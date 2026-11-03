-- ERD v7 → v7.1 (docs/erd_v7_1.sql). V1은 수정하지 않고 변경분만 담는다.
--  * refresh_token.token_hash: CHAR(64) 16진수 → BINARY(32) 원시값(one_time_token과 통일)
--  * one_time_token.purpose: OAUTH_STATE 추가(주석)
--  * users·club profile_image_url: 외부 저장소(S3) URL 주석(image_upload 테이블 없음)
--  * membership.last_viewed_at 추가(최근 본 동아리)
--  * succession_notice 추가(승계 안내 본인별 저장)

-- 해시 형식이 달라 기존 Refresh Token은 사용할 수 없으므로 비운다(인증 구현 전이라 행이 없는 것이 정상. 있다면 재로그인)
UPDATE refresh_token SET replaced_by_id = NULL;
DELETE FROM refresh_token;

ALTER TABLE refresh_token MODIFY COLUMN token_hash BINARY(32) NOT NULL COMMENT '[v7.1] 토큰 값의 SHA-256(32바이트 원시값, one_time_token과 동일 형식). 평문 저장 금지';
ALTER TABLE one_time_token MODIFY COLUMN purpose VARCHAR(30) NOT NULL COMMENT 'OAUTH_STATE(카카오 로그인 시작→콜백, 10분) / LOGIN_EXCHANGE(AUTH-02→03, 60초) / ACCOUNT_RESTORATION(AUTH-03→13, 10분). [v7.1] OAUTH_STATE 저장 확정(10-28 결정)';
ALTER TABLE users MODIFY COLUMN profile_image_url VARCHAR(500) NULL COMMENT '프로필 이미지 링크(UI-3: 최초 카카오 프로필, 직접 업로드 2MB jpg·jpeg·png·webp, NULL이면 이름 첫 글자 아바타). [v6] 탈퇴 시 NULL(원래 값은 user_withdrawal_archive). [v7.1] 외부 저장소(S3) 공개 URL. 업로드 소유·용도는 저장 키 접두사로 검증(image_upload 테이블 없음, U-12)';
ALTER TABLE club MODIFY COLUMN profile_image_url VARCHAR(500) NULL COMMENT '[v7] 동아리 프로필 사진 링크. NULL이면 기본 이미지. [v7.1] 외부 저장소(S3) 공개 URL, 임원만 변경(C-16)';
ALTER TABLE membership ADD COLUMN last_viewed_at DATETIME(6) NULL COMMENT '[v7.1] 이 동아리 홈을 마지막으로 연 시각(HOME-002 최근 본 순 정렬, 로그인 후 기본 동아리 = 가장 최근 값). 갱신 시 updated_at이 바뀌지 않도록 SET updated_at = updated_at 포함' AFTER left_at;

CREATE TABLE succession_notice (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL COMMENT '안내를 받을 사람(권한·팀장을 넘겨받은 사용자)',
    club_id        BIGINT      NOT NULL,
    notice_type    VARCHAR(20) NOT NULL COMMENT 'ADMIN(임원 권한 승계, U-05 ②) / TEAM_LEADER(팀장 승계, U-05 ④)',
    performance_id BIGINT      NULL     COMMENT 'TEAM_LEADER일 때만 값 보유(CHECK)',
    team_id        BIGINT      NULL     COMMENT 'TEAM_LEADER일 때만 값 보유(CHECK). 복합 FK로 같은 공연의 팀임을 보장. 팀이 속한 동아리와 club_id의 일치는 서비스 검증',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at   DATETIME(6) NULL     COMMENT 'NULL이면 미확인. "확인"·"멤버 관리로 이동" 클릭 시 기록',
    PRIMARY KEY (id),
    KEY idx_succession_notice_user_unconfirmed (user_id, confirmed_at),
    KEY idx_succession_notice_club (club_id),
    KEY idx_succession_notice_team (team_id, performance_id),
    CONSTRAINT fk_succession_notice_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_succession_notice_club FOREIGN KEY (club_id) REFERENCES club (id),
    CONSTRAINT fk_succession_notice_team FOREIGN KEY (team_id, performance_id) REFERENCES team (id, performance_id),
    CONSTRAINT ck_succession_notice_team CHECK ((team_id IS NULL) = (performance_id IS NULL)
                                                AND (notice_type = 'TEAM_LEADER') = (team_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '[v7.1] 승계 안내(임원·팀장 승계를 받은 사용자에게 한 번 표시). 확인 후에도 이력으로 남김';
