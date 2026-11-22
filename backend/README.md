# HAMA Backend

목표를 적으면 AI가 할 일로 쪼개 주는 투두 서비스 **하마**의 백엔드입니다.

| | |
|---|---|
| API | https://api.todohama.site |
| 기술 | Spring Boot 4 · Java 21 · MySQL 8 · Flyway · JWT |
| 인프라 | AWS ALB · ASG(EC2, Docker) · RDS · ECR · SSM ([구조도](../README.md#서비스-구조도)) |
| 협업 규칙 | [CONTRIBUTING.md](../CONTRIBUTING.md) |

## 실행 방법

```bash
cd backend
docker compose up -d   # 로컬 MySQL
./gradlew bootRun      # http://localhost:8080
```

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 서버 실행 (Swagger: http://localhost:8080/swagger-ui.html, 로컬에서만) |
| `./gradlew test` | 전체 테스트 + 커버리지 리포트 |
| `./gradlew build` | 배포용 jar |

## 도메인 · 담당

`com.hama.domain` 아래 도메인별 패키지, 공통 코드는 `com.hama.global` 에 있습니다.

| 패키지 | 내용 | 담당 |
| --- | --- | --- |
| `auth` · `user` | 회원가입 · 이메일 인증 · 로그인(JWT) · 계정 찾기 · 탈퇴 | 송승환 |
| `payment` | Paddle 크레딧 결제 · 웹훅 · 환불 | 송승환 |
| `todo` · `schedule` · `calendar` | 투두 · 고정 일정 · 월간 캘린더 · .ics 내보내기 | 장선우 |
| `goal` · `goalai` | 목표 · AI 플랜 생성 · 리포트 · 재계산 | 김지윤 |
| `checkin` | 목표 시작 · 중간 · 종료 체크인 | 김지윤 |
| 인프라 · CI/CD | AWS 배포 · GitHub Actions | 송승환 |

## 설정

- `.env`는 `backend/`에 둡니다(`.env.example` 참고). 없어도 부팅은 됩니다.
- PC에 MySQL이 이미 3306을 쓰고 있으면 `.env`에 `DB_PORT=3307`을 넣습니다.
- 테스트(`./gradlew test`)는 MySQL 컨테이너가 떠 있어야 합니다(`hama_test` DB 사용).
- 테스트가 끝나면 커버리지 리포트(JaCoCo)가 `build/reports/jacoco/test/html/index.html`에 생깁니다. 브라우저로 열면 테스트가 실행하지 않은 줄이 빨갛게 보입니다.
- AI를 실제로 부르려면 `.env`에 `LINER_API_KEY`를 넣습니다. 없어도 AI 외 기능은 정상입니다.
- 구글 캘린더 일회성 가져오기는 `GOOGLE_CALENDAR_CLIENT_ID`, `GOOGLE_CALENDAR_CLIENT_SECRET`,
  `GOOGLE_CALENDAR_REDIRECT_URI`, `GOOGLE_CALENDAR_ENCRYPTION_KEY`가 필요합니다. 키는 32바이트 AES 키를
  Base64로 인코딩한 값이며 비밀 저장소에 보관합니다. 리디렉션 URI는 Google OAuth 설정과 정확히 같아야 합니다.
  값이 없거나 잘못되면 가져오기 API만 사용할 수 없고 서버의 다른 기능은 정상 기동합니다.
  운영에서는 기존 SSM 환경변수 공급 절차를 따르고 Google OAuth 동의 화면·앱 검증 상태를 확인합니다.

## DB 마이그레이션 (Flyway)

테이블은 `src/main/resources/db/migration`의 SQL이 만듭니다. 엔티티와 안 맞으면 서버가 뜨지 않습니다.

- 파일 이름: `V{번호}__{설명}.sql`
- **번호는 쓰기 전에 팀에 말합니다.** 높은 번호가 먼저 운영에 나가면 나중에 온 낮은 번호는 거부됩니다.
- **`develop`에 머지된 파일은 고치지 않습니다.** 고치면 모든 환경에서 부팅이 실패합니다. 새 번호로 `ALTER TABLE`을 씁니다.
- 로컬에서 번호가 꼬여 부팅이 안 되면 DB를 비웁니다(데이터 삭제): `docker compose down -v && docker compose up -d`
- **사용자 데이터 테이블**(`user_id`·`goal_id`·`session_id` 컬럼)을 새로 만들면 `UserService`의 탈퇴 테이블 목록에 추가합니다. 빠뜨리면 테스트가 실패합니다.

## 코드 규칙

새 API는 기존 코드(예: `TodoController`, `GoalController`)를 따라 만듭니다.

- 응답은 `ApiResponse`, 목록은 `PageResponse`로 감쌉니다.
- 에러코드는 자기 도메인 패키지에 `BaseErrorCode` enum으로 만들고 `BusinessException`으로 던집니다.
- 로그인 사용자는 `@AuthenticationPrincipal AuthUser authUser` → `authUser.userId()`로 꺼냅니다.
- 공개 API는 `SecurityConfig.PUBLIC_ENDPOINTS`에만 추가합니다.
- AI 호출은 `AiClient` 하나로 합니다. 테스트에서는 가짜로 바꿉니다.

## Spring Boot 4 주의

Boot 3 자료를 그대로 쓰면 깨집니다.

- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- Jackson 3: `com.fasterxml.jackson.*` → `tools.jackson.*` (`JsonMapper` 사용)
- 테스트 스타터가 모듈별로 나뉨: `spring-boot-starter-webmvc-test` 등
- springdoc은 3.x
