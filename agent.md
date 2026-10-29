agent.md — AI 코딩 도구 작업 지침 (백엔드 저장소)

이 파일은 이 저장소에서 작업하는 AI 코딩 도구(Claude Code, Copilot, Cursor 등)와 사람이 함께 지키는 규칙이다. 규칙과 실제 코드가 다르면 코드를 바꾸기 전에 사람에게 묻는다. 최종 수정: 2026-10-14 (초안)

1. 프로젝트 한 줄 요약

밴드 동아리·단일 밴드의 공연 준비(참여 신청, 팀 구성, 후보곡·투표·가불, 세트리스트, 합주 가능 시간, 일정, 오디션)를 돕는 서비스의 백엔드 API 서버. 프런트엔드(React + Vite + TS)는 별도 저장소.

서비스 이름: 미정. 패키지는 가칭 {basePackage}를 쓴다. 이름이 정해지면 IDE 리팩터링으로 일괄 변경.
시간대: 서비스 기준 KST 고정, 저장은 UTC.
2. 기준 문서 (충돌 시 위에 있는 것이 우선)
   서비스 검증 규칙 (docs/service_validation_rules_v1.md, 노션 「서비스 검증 규칙」과 동일) — 업무 규칙과 오류 코드의 기준. 규칙은 G-01, U-04, C-15처럼 ID로 부른다.
   ERD v6 (docs/erd_v6.sql = Flyway V1__init.sql)
   API 명세 (노션 "API 명세", API-001~125) — URL·요청·응답·공통 응답 형식의 기준.
   화면 명세 (docs/화면명세_v1.0.0.md, Figma) — 문구와 흐름의 기준.

문서끼리 충돌하거나 문서에 없는 동작이 필요하면 추측해서 구현하지 말고 사람에게 묻는다.

3. 버전 고정 (임의로 올리지 않는다)
   항목	버전	주의
   Java	17	toolchain 17
   Spring Boot	3.5.16	Spring Boot 4.x / Spring Framework 7 / Spring Security 7 / Jackson 3 / Hibernate 7 문법을 쓰지 않는다. 예: spring-boot-starter-webmvc·spring-boot-starter-flyway(4.x 이름) 대신 spring-boot-starter-web·flyway-core 사용. 테스트 목은 @MockitoBean(Spring Framework 6.2, @MockBean은 Boot 3.4부터 deprecated)
   의존성 관리 플러그인	io.spring.dependency-management 1.1.7
   Gradle	8.x (Boot 3.5 지원: 7.6.4+ 또는 8.4+)	Groovy DSL(build.gradle)
   Spring Security	6.5.x (Boot 관리)
   Hibernate	6.6.x (Boot 관리)
   QueryDSL	com.querydsl:querydsl-jpa:5.1.0:jakarta	§8 정렬 규칙 필수
   jjwt	0.13.0
   springdoc-openapi	2.9.1
   Flyway	Boot 관리(flyway-core + flyway-mysql)
   MySQL	8.4 LTS	로컬·테스트 모두 mysql:8.4
   Testcontainers	Boot 관리

Boot가 관리하는 라이브러리는 build.gradle에 버전을 쓰지 않는다.

4. 자주 쓰는 명령
   bash
   ./gradlew spotlessApply      # 포맷 자동 정리 (커밋 전 필수)
   ./gradlew spotlessCheck checkstyleMain   # CI와 같은 검사
   ./gradlew test               # Testcontainers 사용 → Docker 실행 중이어야 함
   ./gradlew bootRun --args='--spring.profiles.active=local'
   docker compose up -d         # 로컬 MySQL 8.4
5. 패키지 구조
   {basePackage}
   ├── global/                 # 도메인 공통
   │   ├── config/             # Web, JPA, QueryDSL, Cache(Caffeine), Jackson, OpenAPI
   │   ├── security/           # 쿠키 JWT 필터, CSRF 헤더 검사, CORS, 인증 주체
   │   ├── error/              # ErrorCode(검증 규칙 0.2), 예외 클래스, 전역 예외 처리
   │   ├── response/           # 공통 응답 형식(API 명세 기준), offset 페이지 응답
   │   ├── entity/             # 생성·수정 시각 기본 엔티티
   │   └── audit/              # audit_log 기록
   └── domain/
   ├── auth/  user/  club/  membership/  part/
   ├── performance/  participant/  team/  song/
   ├── vote/  playability/  setlist/  availability/
   ├── schedule/  audition/  home/  admin/
   └── (각 도메인) controller / service / repository / dto / entity
   의존 방향: controller → service → repository. controller에서 repository 직접 호출 금지.
   다른 도메인의 repository를 직접 쓰지 않고 그 도메인의 service를 호출한다.
   도메인 분담(리뷰어 참고): 혜성 = auth·user·club·admin·home / 규채 = membership·performance·participant·schedule·audition / 강욱 = song·team·setlist·availability·vote·playability.
6. 코드 규칙
   포맷: Spotless + palantir-java-format(4칸, 120자). 손으로 맞추지 말고 spotlessApply.
   객체 생성: 파라미터가 많으면 빌더. 엔티티는 protected 기본 생성자 + 빌더/정적 팩토리.
   DTO: record 대신 일반 클래스 + Lombok @Getter·@Builder(필요 시 @NoArgsConstructor(access = PROTECTED)). @Data, @Setter 금지.
   서비스: 인터페이스 없이 구체 클래스 하나. 조회는 @Transactional(readOnly = true).
   엔티티: setter 대신 의미 있는 메서드(approve(), withdraw()). 상태 전이 규칙은 엔티티 메서드나 서비스에서 검증 규칙 ID를 주석으로 남긴다.
   enum: DB에는 대문자 문자열(@Enumerated(EnumType.STRING)). DB에 값 목록 CHECK 없음 → 값 검증은 애플리케이션 책임.
   null: 컬렉션은 null 대신 빈 컬렉션 반환.
   로그: 개인정보(이름, 카카오 식별자, 토큰, IP 전체) 로그 금지. 감사 기록 detail에는 ID와 변경 전·후 값만(U-09).
7. API 규칙
   공통 응답·오류 본문 모양은 API 명세의 공통 규칙을 따른다. 명세에 없으면 묻는다.
   오류 코드는 검증 규칙 0.2의 9종만 사용: VALIDATION_ERROR(400), UNAUTHENTICATED(401), FORBIDDEN(403), NOT_FOUND(404), STATE_CONFLICT(409), DUPLICATE(409), VERSION_CONFLICT(409), CONFIRMATION_REQUIRED(409), RULE_VIOLATION(422).
   다른 동아리·다른 공연의 리소스는 404(존재 비노출, G-04).
   강도: "확인 필요" 규칙은 confirmed=true 없으면 409 + 경고 목록, "경고" 규칙은 성공 응답의 warnings.
   DB 제약 위반(1062, 1451, 1452, 3819)을 그대로 내보내지 않는다. 서비스에서 먼저 검사해 위 코드로 변환.
   일시: 요청·응답 모두 ISO-8601 + +09:00. 날짜만은 YYYY-MM-DD. 저장은 UTC(hibernate.jdbc.time_zone=UTC).
   목록: offset 방식 페이지네이션(파라미터·응답 이름은 API 명세 기준).
   낙관적 락 대상(performance, team, song, practice_schedule)은 요청 바디에 version.
   GET은 데이터를 바꾸지 않는다.
8. 데이터베이스
   스키마 변경은 Flyway 마이그레이션 파일로만(V{n}__설명.sql). 이미 머지된 마이그레이션 파일은 수정 금지.
   JPA ddl-auto=validate. 엔티티와 스키마가 다르면 기동 실패가 정상.
   club.name_key는 DB 생성 컬럼 → @Column(insertable = false, updatable = false).
   물리 삭제는 검증 규칙 16장 테이블만. 나머지는 상태 전이.
   여러 행에 걸친 조건(한도, 최소 인원)은 기준 행 SELECT … FOR UPDATE 후 한 트랜잭션(0.4).
   QueryDSL 정렬: 사용자 입력을 orderBy에 그대로 넣지 않는다. 허용 필드 목록(enum)으로만 매핑(CVE-2024-49203).
   N+1 주의: 목록 조회는 fetch join 또는 DTO 프로젝션.
9. 인증·보안 (검증 규칙 0.5, G-01~G-03, U-04~U-11)
   Access Token 15분, Refresh Token 30일, 둘 다 HttpOnly; Secure; SameSite=Lax 쿠키(Domain 생략).
   Refresh Token: DB가 원본(SHA-256 해시 저장), Caffeine은 캐시. 재발급마다 로테이션, 이미 교체된 토큰이 오면 같은 family_id 전부 폐기. 만료 = min(발급+30일, 최초 로그인+90일). 만료+7일 후 삭제.
   현재 기기 로그아웃은 jti 블랙리스트, 전체 무효화는 users.token_version + 1. token_version은 모든 요청에서 비교.
   상태 변경 요청에 X-Requested-With 헤더가 없으면 403. CORS는 FE 출처 하나만.
   역할은 토큰이 아니라 요청마다 membership.role로 확인(G-07).
   비밀값(카카오 키, JWT 서명 키, DB 비밀번호)은 절대 커밋 금지. application-local.yml(gitignore) 또는 환경 변수.
10. 테스트
    DB가 필요한 테스트는 Testcontainers(mysql:8.4)만 사용. H2 금지(REGEXP_REPLACE 생성 컬럼을 지원하지 않음).
    컨테이너는 공통 @TestConfiguration + @ServiceConnection 하나를 @Import해서 공유.
    @DataJpaTest에는 @AutoConfigureTestDatabase(replace = Replace.NONE).
    업무 규칙 테스트의 이름이나 @DisplayName에 규칙 ID를 넣는다(예: "C-15 공백·대소문자만 다른 동아리 이름은 409").
    새 기능 PR에는 최소한 성공 1건 + 해당 규칙 위반 1건 테스트.
11. Git 작업 방식
    브랜치: main(배포) / dev(통합, 기본 브랜치) / feat/#이슈번호(작업).
    feat → dev는 PR + 승인 1명 + Squash merge. dev → main은 PR로만.
    커밋 메시지 규칙: 미정(확정 전까지 타입: 내용 (#이슈) 형식 권장, 예: feat: 카카오 로그인 콜백 구현 (#12)).
    PR 설명에 관련 API ID, 검증 규칙 ID, 화면 ID를 적는다.
    main·dev에 직접 push 하지 않는다. force push 금지.
12. AI 도구가 반드시 묻고 진행할 것
    기준 문서에 없는 동작, 문서끼리 충돌하는 동작
    새 라이브러리 추가, 버전 변경
    스키마 변경(새 마이그레이션)
    공통 모듈(global/) 변경
    오류 코드·응답 형식 변경
    보안 설정(필터 순서, CORS, 쿠키 속성) 변경
13. 하지 말 것
    Spring Boot 4.x 예제 코드를 그대로 가져오기
    @Data, @Setter, 필드 주입(@Autowired 필드) 사용
    System.out.println, 비밀값 하드코딩
    테스트를 지우거나 @Disabled로 통과시키기
    마이그레이션 파일 수정, ddl-auto=update/create
    검증 규칙을 코드에 반영하면서 규칙 ID를 남기지 않기