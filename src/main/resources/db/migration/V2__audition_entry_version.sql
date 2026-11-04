ALTER TABLE audition_entry
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
    COMMENT 'API-149 오디션 결과 입력·정정의 낙관적 락 버전';
