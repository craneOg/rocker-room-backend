# band-web-backend

밴드 동아리 공연 준비 서비스(rocker-room)의 백엔드 API 서버. 규칙은 `agent.md` 참고.

## 로컬 실행

1. MySQL 8.4: `docker compose up -d`
2. 환경 변수: `JWT_SECRET`(필수, 32바이트 이상), `APP_CORS_ORIGIN`(선택, 기본 `http://localhost:5173`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`

Swagger UI: `http://localhost:8080/swagger-ui/index.html` (local 프로필에서만 노출, 운영·스테이징은 비노출)

## 테스트

`./gradlew spotlessApply spotlessCheck checkstyleMain checkstyleTest test` (Docker 실행 필요, Testcontainers)

## 다음 작업

auth 도메인 구현 시 입력:
- 카카오 로그인 연동, 로그인·재발급·로그아웃 API
- 리프레시 토큰 저장(SHA-256 해시, 로테이션, family 폐기)
- 로그아웃 시 액세스 토큰 jti를 블랙리스트에 기록
- 쿠키 발급·삭제는 `CookieFactory`로만 한다
