# PR #18 추가 리뷰 반영 및 동시성 검증

검증일: 2026-11-12. 커밋·푸시하지 않은 작업 결과다.

## 항목별 처리 기준

| 항목 | 적용한 기준 |
| --- | --- |
| 1. API-150 | requireMember와 같은 소속 상태 판정(ACTIVE·ON_LEAVE·GRADUATED)을 재사용한다. PerformanceService.list의 기존 공연 공개 규칙으로 결과를 제한한다. 접근 불가 기록만 제외하고 정상 항목·필터·정렬·묶음·isLatest는 유지한다. 심사 이력은 삭제하지 않는다. 비활성 동아리의 읽기 허용 등 기존 접근 규칙도 변경하지 않는다. |
| 2. 등록 중복 | 공연 → 곡 → 심사 순서로 잠근다. 곡 잠금을 유지하고 곡 심사 또는 곡·파트·요청 지원자의 미심사 행만 전용 PESSIMISTIC_WRITE 조회로 읽는다. 일반 목록 조회와 분리한다. 기존 전체 거절·대상별 details·재심 허용은 유지한다. |
| 3. 심사와 확정 | 심사는 공연 잠금을 획득한 뒤 상태·사유를 확인하고 심사 행을 잠가 version을 비교한다. 확정은 공연 → 곡 → 확정 준비 데이터 순서다. LOCKED 등록 허용과 LOCKED 결과 변경의 사유·예외 감사는 유지한다. |
| 4. 비활성 동아리 심사 | 최초 입력과 정정 모두 STATE_CONFLICT / CLUB_INACTIVE로 거절하고 기존 심사·감사는 보존한다. |
| 5. 일괄 가입 결정 | STATE_CONFLICT + INVALID_STATE만 NOT_PENDING으로 변환한다. CLUB_INACTIVE 등 다른 사유와 기존 부분 성공 결과는 유지한다. |
| 6. 비활성 동아리 참여 | PerformanceMemberService.register의 APPLICATION·ADMIN·재신청 분기 이전에 공통 활성 검사를 적용한다. |
| 7. 참여와 확정 | register가 확정과 같은 공연 잠금을 사용한다. LockReadinessReader.readLocked는 확정 트랜잭션에 참여하며 모든 준비 데이터 SELECT를 FOR UPDATE로 읽는다. 미리보기 read는 일반 조회다. 전역 격리 수준은 바꾸지 않는다. |
| 8. 일정 제목 | ScheduleTitles로 자동 생성 형식을 공유하고, 입력 또는 생성이 끝난 최종 제목의 100자 제한을 검사한다. 생성·수정·각 사전 검사에서 같은 기준을 적용하며 저장 전 VALIDATION_ERROR를 반환한다. |
| 9. 충돌 공개 범위 | 대상 일정의 공연 접근을 확인하고, 상세 충돌 후보도 같은 공연 공개 규칙으로 제한한다. 기존 비임원 TENTATIVE 제한·본인 참석 필터·임원 조회 범위와 다른 동아리 익명 충돌 형식은 유지한다. |

## REPEATABLE READ 재현과 선택 근거

수정 전 실제 MySQL 8.4의 독립 트랜잭션 두 개로 다음 순서를 실행했다.

1. 확정 측 트랜잭션을 REPEATABLE READ로 시작하고 참여자 일반 SELECT로 스냅샷을 만든다.
2. 별도 트랜잭션에서 신규 신청 또는 WITHDRAWN·REJECTED 참여 재신청을 완료하고 커밋한다.
3. 확정 측 일반 SELECT가 여전히 APPLIED 0건을 읽는 것을 확인한다.
4. 같은 확정 측 트랜잭션에서 실제 PerformanceLockService.lock을 호출한다.

수정 전 세 경우 모두 L-05로 거절해야 한다는 테스트가 실패했다. 실제 오류는 `Expecting code to raise a throwable`였고 확정 호출이 성공했다. 따라서 공연 잠금 획득만 추가하는 것으로는 부족하다.

수정 후 동일한 순서를 유지하면서 확정 준비 조회만 최신 잠금 읽기로 변경했다. 일반 SELECT는 여전히 이전 스냅샷을 읽지만 실제 확정은 새 APPLIED 참여자를 발견해 RULE_VIOLATION / APPLIED_PARTICIPANT로 거절한다. 스냅샷 격리 수준을 바꾸거나 새 트랜잭션으로 검사를 분리하지 않았다.

## 테스트

- `Pr18ConcurrencyIntegrationTest`: 14건. 두 실제 애플리케이션 연결의 CONNECTION_ID가 다름을 확인한다. 경합 테스트는 첫 변경을 커밋 전에 보류하고, 두 번째 요청의 `information_schema.innodb_trx`에 실제 LOCK WAIT가 나타난 뒤 첫 커밋을 허용한다. root 연결은 테스트 컨테이너의 잠금 상태 관측에만 사용한다.
  - 이전 스냅샷 이후 신규·WITHDRAWN·REJECTED 재신청: 3건.
  - SONG·SESSION 동일 대상 동시 등록: 2건. 한 행과 한 생성 감사만 남고 후속 요청은 DUPLICATE.
  - 신규·재신청 후 확정 및 확정 후 신규·재신청: 6건. 선행 커밋에 따라 L-05 또는 LOCKED 상태 제한 적용.
  - 심사 완료 후 확정: 1건. 최신 결과를 읽어 L-06 대기를 놓치거나 잘못 남기지 않음.
  - 확정 후 정정: 사유 있음·없음 2건. LOCKED 사유 검사와 예외 감사 적용.
- `Pr18PolicyIntegrationTest`: 21건. 접근 불가 항목만 제외, 기존 읽기 권한 보존, 비활성 심사·참여 등록, 최종 제목 경계와 초과 거부, 충돌 정보 공개 범위를 실제 DB에서 검증한다.
- `JoinBulkDecisionServiceTest`: INVALID_STATE·CLUB_INACTIVE·사유 없는 STATE_CONFLICT·기타 사유·NOT_FOUND 매핑 회귀 검증 추가.
- 최종 실행: `./gradlew spotlessApply spotlessCheck checkstyleMain test --console=plain` 성공.
- 전체 398건, 실패 0, 오류 0, 건너뜀 0. `git diff --check` 통과.

## 명세 반영

최신 Notion의 설명 속성과 본문에 아래 내용을 직접 반영했다.

- [API-150](https://www.notion.so/95b2145773468252b5d7819dcbbc0451): 접근 불가 항목만 제외하고 원본 이력과 정상 항목 보존.
- [API-148](https://www.notion.so/38021457734682aba98b011f59e0e631), [API-149](https://www.notion.so/5652145773468266be16012ede6a0a3a): 잠금 순서·최신 중복 조회·비활성 심사 거부.
- [API-051](https://www.notion.so/9b8214577346834db78e810090bac594): 최신 확정 준비 조회와 등록·정정 직렬화.
- [API-055](https://www.notion.so/78021457734683ad9b45012b28b18fbd), [API-056](https://www.notion.so/6b82145773468311879701f0ca4125b2): 공통 활성 검사와 확정 후 상태 제한.
- [API-143](https://www.notion.so/59f21457734683f4ad80817fb1527f80): 항목별 세부 오류 사유 보존.
- [API-106](https://www.notion.so/f60214577346828cb79a81e80e912af1), [API-107](https://www.notion.so/2cc214577346836ab1e10131f60bb125), [API-109](https://www.notion.so/5c821457734682e098ac811fc6b72f1e), [API-110](https://www.notion.so/e9c21457734682a5b885017978873adf): 최종 제목 제한의 일관성.
- [API-113](https://www.notion.so/675214577346833786b501e628ebd5a7): 대상·상세 충돌 후보의 공연 공개 범위와 기존 익명 정책 유지.

## 검증 범위와 남은 사항

- 동시성 검증 대상은 이 문서의 오디션 등록·심사와 PerformanceMemberService.register 및 공연 확정이다. 다른 도메인의 팀 신청·자동 편입 등 별도 참여 생성 경로 전체의 동시성까지 검증한 것은 아니다.
- 같은 공연의 변경을 직렬화하므로 잠금 경합에 따른 실제 운영 처리량은 부하 테스트가 필요하다. 이번 테스트는 정확한 실행 순서와 정합성 검증이다.
- 최신 develop 통합 및 기존 PR의 다른 미해결 리뷰는 이번 9개 항목과 별도다. DB 스키마·공통 오류 형식·전역 격리 수준은 변경하지 않았다.
