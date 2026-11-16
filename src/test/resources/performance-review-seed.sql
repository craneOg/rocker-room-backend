INSERT INTO users (id, name, handle) VALUES (4, '객원', 'guest_4');
INSERT INTO membership (id, club_id, user_id, member_type, role, status, join_source)
VALUES (4, 1, 4, 'GUEST', 'MEMBER', 'ACTIVE', 'ADMIN_ADDED');
INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)
VALUES (4, 1, 1, 4, 'APPROVED', 'ADMIN');
INSERT INTO practice_schedule (id, performance_id, schedule_type, status, title, start_at, end_at,
                               for_all_participants, created_by_user_id)
VALUES (10, 1, 'PRACTICE', 'CONFIRMED', '다른 팀 일정', '2099-01-01 00:00:00', '2099-01-01 01:00:00', FALSE, 1),
       (11, 1, 'PRACTICE', 'CONFIRMED', '전체 일정', '2099-01-02 00:00:00', '2099-01-02 01:00:00', TRUE, 1);
