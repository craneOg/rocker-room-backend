-- #50: 가능 시간 미입력과 '가능한 시간 없음' 저장을 구분한다(AV-03: 빈 목록 저장은 슬롯 0건이라 구분 불가).
-- API-101 저장 때마다 기록하며, NULL이면 미입력이다. 기존 슬롯 보유자는 첫 슬롯 생성 시각으로 채운다.
ALTER TABLE performance_member
    ADD COLUMN availability_submitted_at DATETIME(6) NULL
        COMMENT '가능 시간 제출 시각(API-101 저장 시 기록, 빈 목록 저장 포함). NULL이면 미입력';

UPDATE performance_member pm
    JOIN (SELECT performance_member_id, MIN(created_at) AS first_at
          FROM available_slot
          GROUP BY performance_member_id) s ON s.performance_member_id = pm.id
SET pm.availability_submitted_at = s.first_at;
