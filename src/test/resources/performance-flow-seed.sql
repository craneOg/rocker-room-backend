-- 공연 준비 흐름 통합 테스트용 최소 데이터. 1 = 임원, 2·3 = 부원
INSERT INTO users (id, name, handle) VALUES (1, '임원', 'admin_1'), (2, '부원A', 'member_a'), (3, '부원B', 'member_b'), (4, '탈퇴자', 'member_c');
INSERT INTO club (id, name, created_by) VALUES (1, '테스트밴드', 1);
INSERT INTO membership (id, club_id, user_id, role, status, join_source) VALUES
    (1, 1, 1, 'CLUB_ADMIN', 'ACTIVE', 'CLUB_CREATOR'),
    (2, 1, 2, 'MEMBER', 'ACTIVE', 'JOIN_CODE'),
    (3, 1, 3, 'MEMBER', 'ACTIVE', 'JOIN_CODE'),
    (4, 1, 4, 'MEMBER', 'WITHDRAWN', 'JOIN_CODE');
INSERT INTO part (id, club_id, name, display_order) VALUES (1, 1, '보컬', 1), (2, 1, '기타', 2);
INSERT INTO membership_part (membership_id, part_id) VALUES (2, 1);
INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id) VALUES (1, 1, '정기공연', 'CUSTOM', 'ACTIVE', 1);
INSERT INTO performance_policy (performance_id, candidate_state, playability_state, vote_state, vote_method,
                                vote_max_choices, team_submission_state, availability_state)
VALUES (1, 'OPEN', 'OPEN', 'OPEN', 'APPROVAL', 1, 'OPEN', 'OPEN');
INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source) VALUES
    (2, 1, 1, 2, 'APPROVED', 'APPLICATION');
