# HAMA Backend 개발 가이드

Spring Boot 4 · Java 21 · Gradle · MySQL 8 · Flyway. 팀 공통 규칙과 API 응답 형식은 [루트 README](../README.md)에 있습니다.

## 로컬 실행

```bash
docker compose up -d      # MySQL 8.0 (hama, hama_test DB 생성)
./gradlew bootRun         # 기본 프로필 local
./gradlew test            # MySQL 컨테이너가 떠 있어야 합니다 (hama_test 사용)
```

- `.env`는 `backend/`에 둡니다. `.env.example`을 복사해 쓰세요. 없어도 부팅은 됩니다.
- PC에 MySQL이 따로 깔려 3306을 쓰고 있으면 `.env`에 `DB_PORT=3307`처럼 다른 포트를 넣습니다.
- Flyway 오류로 부팅이 안 되면(번호를 바꿨거나 예전 테이블이 남은 경우) 로컬 DB를 비웁니다. 데이터가 전부 지워집니다.
  `docker compose down -v && docker compose up -d`

**Swagger**: http://localhost:8080/swagger-ui.html
보호된 API는 `POST /api/auth/signup`(또는 `/login`) 응답의 `data.accessToken`을 우측 상단 **Authorize**에 붙여 넣습니다(`Bearer ` 없이).

## 인증

| 토큰 | 전달 | 수명 | 저장 |
|---|---|---|---|
| Access | body `data.accessToken` → 헤더 `Authorization: Bearer` | 30분 | 프론트 메모리 |
| Refresh | `Set-Cookie: refreshToken` (HttpOnly, `Path=/api/auth`) | 14일 | DB에는 SHA-256 해시만, **기기마다 한 행** |

- 재발급할 때마다 리프레시 토큰을 새로 바꿉니다(rotation). 로그인은 이메일당 15분에 5회까지입니다.
- 컨트롤러에서 로그인한 사용자는 이렇게 꺼냅니다. 토큰이 없으면 컨트롤러에 오기 전에 401이 나가므로 null 검사는 필요 없습니다.
  ```java
  public ApiResponse<TodoResponse> get(@AuthenticationPrincipal AuthUser authUser) {
      Long userId = authUser.userId();
  ```
- 공개 API는 `SecurityConfig.PUBLIC_ENDPOINTS`에만 추가합니다. `/actuator/**`처럼 넓게 열지 않습니다.
- 테스트에서 인증이 필요하면 `/api/auth/signup`으로 받은 토큰을 붙입니다(`AuthApiIntegrationTest` 참고).

## 코드 규칙

**응답은 `ApiResponse`로 감쌉니다.** 에러 응답은 `GlobalExceptionHandler`가 만들므로 컨트롤러에서 만들지 않습니다.

```java
return ApiResponse.success(todoResponse);   // 데이터 있음
return ApiResponse.noContent();             // 데이터 없음
```

**목록은 `PageResponse`로 감쌉니다.** `PageResponse.pageRequest(page, size)`가 범위를 검사합니다(size 1~100).
Spring `Page`를 그대로 반환하지 않습니다. 정렬은 쿼리의 `order by`로 정하고 클라이언트 `sort`는 받지 않습니다(예: `GoalController.list`).

**에러코드는 자기 도메인 패키지에 만듭니다.** `GlobalErrorCode`에 넣으면 여럿이 한 파일을 고쳐 충돌이 납니다.

```java
@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements BaseErrorCode {
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "투두를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}

throw new BusinessException(TodoErrorCode.TODO_NOT_FOUND);
throw new BusinessException(code, e);   // 5xx 는 원인 예외를 같이 넘겨야 로그에 스택이 남습니다
```

**엔티티**
- `BaseTimeEntity`를 상속합니다(`created_at`, `updated_at` 자동).
- 생성은 정적 팩토리(`create`, `of`, `from`)로만 합니다. 빌더는 `@Builder(access = PRIVATE)`로 막습니다.
- 상태 변경은 setter 대신 의도가 드러나는 메서드로 합니다(`user.markPremium()`).
- PK 컬럼명은 `테이블명_id`입니다. 예시: `domain/user/entity/User`, `domain/auth/entity/RefreshToken`

**Swagger 설명**: 컨트롤러에 `@Tag`/`@Operation`, DTO에 `@Schema`를 답니다.

## DB 스키마 (Flyway)

스키마는 `src/main/resources/db/migration`의 SQL이 만들고, Hibernate는 엔티티와 맞는지 검사만 합니다(`ddl-auto: validate`).

- 파일 이름은 `V{번호}__{설명}.sql`입니다(언더스코어 두 개).
- **번호는 쓰기 전에 팀에 말합니다.** 운영 DB는 outOfOrder가 꺼져 있어서, 높은 번호가 먼저 배포되면 나중에 온 낮은 번호를 거부합니다.
- **이미 `develop`에 머지된 파일은 고치지 않습니다.** 체크섬이 바뀌어 모든 환경에서 부팅이 실패합니다. 새 번호로 `ALTER TABLE`을 씁니다.
- SQL은 직접 짜지 않아도 됩니다. `bootRun`하면 엔티티 기준 DDL이 `build/generated-schema.sql`에 생기니 필요한 부분을 옮깁니다.
- **사용자 데이터 테이블**(`user_id`·`goal_id`·`session_id` 컬럼이 있는 테이블)을 새로 만들면, 탈퇴 때 지우도록
  `UserService`의 테이블 목록에 추가합니다. 빠뜨리면 `UserWithdrawApiIntegrationTest`가 실패합니다.

## AI 호출 (`global/ai`)

AI가 필요한 곳은 전부 `AiClient` 하나로 라이너 [Chat Completions](https://liner.com/developers/docs/liner-model-api-chat-completions)(OpenAI 호환)를 부릅니다.

```java
String text = aiClient.chat(AiRequest.of("weekly-review", "너는 목표 달성 코치야.", summary));
RealityResult result = aiClient.chatForJson(
        AiRequest.of("reality-check", "verdict, comment 필드를 가진 JSON 으로 답해.", goal), RealityResult.class);
```

- 대화형은 `AiRequest.of(purpose, systemPrompt, List<AiMessage>)`에 이력을 오래된 것부터 넣습니다.
- `purpose`는 로그 태그입니다(`[AI] purpose=... promptTokens=...`).
- 토큰 절감(`max-history-messages`, `max-completion-tokens`, `reasoning-effort`)은 `application.yml`의 `liner.*`에서만 조정합니다. 도메인 코드에서 이력을 자르지 않습니다.
- 실패는 전부 `BusinessException`이니 잡지 말고 던집니다: `AI_RATE_LIMIT`(429), `AI_UPSTREAM_ERROR`(502), `AI_NOT_CONFIGURED`(503).
- 로컬에서 실제로 부르려면 `.env`에 `LINER_API_KEY`를 넣습니다. 테스트는 `AiClient`를 가짜로 바꿉니다(`application-test.yml`은 키가 비어 있음).

## Spring Boot 4 주의사항

Boot 3 기준 자료를 그대로 가져오면 깨지는 부분입니다.

- 웹 스타터: `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- Jackson 3: `com.fasterxml.jackson.*` → `tools.jackson.*` (`ObjectMapper` 대신 `JsonMapper`). 어노테이션 패키지는 그대로입니다.
- 테스트 스타터가 모듈별로 나뉩니다: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` 등
- `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`
- springdoc은 3.x를 씁니다(2.x는 Boot 3 전용).
