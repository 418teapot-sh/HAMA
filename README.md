# HAMA

프론트엔드와 백엔드를 한 레포에서 관리하는 모노레포입니다.

## 구조

```
HAMA/
├── backend/            # Spring Boot 4 (Java 21, Gradle)
│   ├── src/
│   ├── docker/         # 로컬 MySQL 초기화 스크립트
│   ├── docker-compose.yml
│   └── build.gradle
├── frontend/           # 프론트엔드 (별도 초기화 예정)
└── .github/
    └── workflows/
        └── ci-backend.yml
```

각 앱은 자기 폴더 안에서 독립적으로 빌드·실행합니다. 루트에는 빌드 설정이 없습니다.

## CI

- 앱별로 워크플로우를 나눕니다 (`ci-backend.yml`, 이후 `ci-frontend.yml`).
- `pr-title.yml`은 PR 제목이 [협업 규칙](#협업-규칙) 형식인지 검사합니다. 제목만 고쳐도 다시 돕니다.
- job id가 곧 룰셋 필수 체크 이름입니다. job id를 바꾸면 룰셋도 같이 바꿔야 합니다.
- **필수 체크 워크플로우에는 `paths` 필터를 걸지 않습니다.**
  프론트만 바뀐 PR에서 `ci-backend`가 실행되지 않으면 필수 체크가 "대기 중"으로 남아 머지가 영구히 막힙니다.
  경로 필터는 배포 워크플로우에만 겁니다.

## Backend 로컬 실행

사전 준비: JDK 21, Docker

```bash
cd backend
docker compose up -d      # MySQL 8.0 (hama, hama_test DB 생성)
./gradlew bootRun         # 기본 프로필: local
```

- `.env`는 `backend/`에 둡니다. `backend/.env.example`을 복사해서 쓰세요. 없어도 부팅은 됩니다.
- 테스트: `./gradlew test` (MySQL 컨테이너가 떠 있어야 합니다. `hama_test` DB 사용)

### Swagger

- UI: http://localhost:8080/swagger-ui.html
- OpenAPI 스펙: http://localhost:8080/v3/api-docs

보호된 API를 테스트하려면: `POST /api/auth/signup`(또는 `/login`) → 응답의 `data.accessToken` 복사 →
우측 상단 **Authorize**에 토큰만 붙여넣기(`Bearer ` 없이).

## Backend: 인증 (JWT)

| 토큰 | 전달 방식 | 수명 | 프론트 보관 |
|---|---|---|---|
| Access Token | 응답 body `data.accessToken` → 요청 헤더 `Authorization: Bearer <토큰>` | 30분 | **메모리** (변수·상태). localStorage 금지 |
| Refresh Token | `Set-Cookie: refreshToken` (HttpOnly, `Path=/api/auth`) | 14일 | 브라우저가 자동 관리. JS로 읽을 수 없음 |

- Refresh Token은 body에 오지 않습니다. JS가 읽을 수 없는 쿠키라 XSS가 나도 14일짜리 토큰은 털리지 않습니다.
- 서버 DB에는 Refresh Token의 SHA-256 해시만 저장하고, 재발급할 때마다 새 토큰으로 교체(rotation)합니다.
- 사용자당 Refresh Token은 하나입니다. 다른 기기에서 로그인하면 이전 기기는 30분 뒤 다시 로그인해야 합니다.

### API

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/auth/signup` | `{ email, password(8~64자), nickname(≤30자) }` | `accessToken` + 쿠키. 중복 이메일 409 `EMAIL_ALREADY_EXISTS` |
| POST | `/api/auth/login` | `{ email, password }` | `accessToken` + 쿠키. 실패 401 `INVALID_CREDENTIALS`, 15분에 5회 초과 429 |
| POST | `/api/auth/refresh` | body 없음 (쿠키만) | 새 `accessToken` + 새 쿠키. 실패 401 `INVALID_REFRESH_TOKEN` → 다시 로그인 |
| POST | `/api/auth/logout` | body 없음 (쿠키만) | 쿠키 삭제 + 서버 토큰 삭제. 항상 성공 |
| GET | `/api/users/me` | Bearer | 내 정보 |

### 프론트 연동

```ts
// 쿠키가 오가려면 인증 요청에 반드시 credentials 를 켭니다. (axios: withCredentials: true)
const res = await fetch(`${API}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',
  body: JSON.stringify({ email, password }),
});
accessToken = (await res.json()).data.accessToken;   // 메모리에만

// API 호출이 401 UNAUTHORIZED 면 → /api/auth/refresh (credentials: 'include') 로 재발급 후 재시도.
// 새로고침으로 메모리가 날아가도 앱 시작 시 /api/auth/refresh 를 한 번 부르면 로그인이 복구됩니다.
```

### 백엔드: 로그인한 사용자 id 꺼내기

지금 `Long userId = 1L;`로 임시 작업 중인 곳은 이렇게 바꾸면 됩니다.

```java
// Before
@GetMapping("/api/todos")
public ApiResponse<List<TodoResponse>> getTodos() {
    Long userId = 1L;
    ...
}

// After
@GetMapping("/api/todos")
public ApiResponse<List<TodoResponse>> getTodos(@AuthenticationPrincipal AuthUser authUser) {
    Long userId = authUser.getUserId();
    ...
}
```

- `AuthUser`는 `com.hama.global.auth.AuthUser`, `@AuthenticationPrincipal`은 `org.springframework.security.core.annotation`입니다.
- `/api/auth/**`, Swagger, `/actuator/health`·`/actuator/info` 외에는 **전부 로그인 필요**입니다.
  토큰이 없거나 틀리면 컨트롤러에 오기 전에 401 `UNAUTHORIZED`가 나가므로, 컨트롤러에서 `authUser`가 null인지 검사할 필요 없습니다.
- 공개 API를 새로 만들어야 하면 `SecurityConfig.PUBLIC_ENDPOINTS`에 추가합니다. `/actuator/**`처럼 넓게 열지 마세요.
- 테스트에서 인증된 요청이 필요하면 `/api/auth/signup`으로 받은 토큰을 헤더에 붙입니다(`AuthApiIntegrationTest` 참고).

## Backend: global 패키지 사용 규칙

`com.hama.global`에는 모든 도메인이 같이 쓰는 코드가 있습니다.

**1. 컨트롤러는 `ApiResponse`로 감싸서 반환합니다.**

```java
return ApiResponse.success(todoResponse);   // 데이터 있음
return ApiResponse.noContent();              // 데이터 없음 (DELETE 등)
```

에러 응답은 `GlobalExceptionHandler`가 만들어주니 컨트롤러에서 직접 만들지 않습니다.

### 응답 형식 (프론트 연동 계약)

모든 API는 아래 두 형식 중 하나로만 응답합니다. 프론트는 `/v3/api-docs`로 이 형식의 타입을 생성합니다.

```jsonc
// 성공
{ "success": true,  "data": <T>,  "error": null, "traceId": "a1b2c3d4e5f6a7b8" }

// 실패
{ "success": false, "data": null, "error": { "code": "...", "message": "...", "fields": null }, "traceId": "..." }
```

- **실패면 `data`는 항상 `null`** 입니다. 에러 정보는 전부 `error` 안에 있습니다.
- `error.fields`는 **검증 실패(`VALIDATION_FAILED`)일 때만** `{ "필드명": "메시지" }`가 오고, 그 외에는 `null`입니다.
  한 필드가 여러 검증을 어기면 메시지가 `", "`로 이어져 옵니다.

  ```json
  { "success": false, "data": null,
    "error": { "code": "VALIDATION_FAILED", "message": "입력 데이터 검증에 실패했습니다.",
               "fields": { "title": "제목은 필수입니다.", "memo": "메모는 10자 이하입니다." } },
    "traceId": "..." }
  ```
- 5xx 에러의 `message`는 에러코드의 기본 문구만 옵니다. 상세 원인은 서버 로그에만 남습니다.
- 에러 분기는 `message`가 아니라 **`error.code`** 로 합니다. 문구는 바뀔 수 있습니다.

**2. 에러코드는 자기 도메인 패키지에 만듭니다.**

```java
@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements BaseErrorCode {
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "투두를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}

throw new BusinessException(TodoErrorCode.TODO_NOT_FOUND);
```

- `GlobalErrorCode`에는 도메인 에러를 추가하지 않습니다. 여럿이 한 파일을 고치면 merge 충돌이 계속 납니다.
- 5xx 에러는 원인 예외를 같이 넘깁니다: `new BusinessException(code, e)`. 그래야 로그에 스택이 남습니다.

**3. 엔티티는 `BaseTimeEntity`를 상속합니다.**

```java
@Entity
public class Todo extends BaseTimeEntity { ... }
```

`created_at`, `updated_at`이 자동으로 채워집니다.

**4. 엔티티는 정적 팩토리 메서드로만 생성합니다. (팀 컨벤션)**

빌더는 내부에서만 쓰고 외부에 열지 않습니다. 생성 시점의 불변식(기본값, 필수값)을 팩토리 한 곳에서 강제해서,
호출부가 빌더로 필드를 빠뜨리거나 엉뚱한 값을 넣을 여지를 없앱니다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 용
@Builder(access = AccessLevel.PRIVATE)               // 외부에서 User.builder() 못 씀
@AllArgsConstructor(access = AccessLevel.PRIVATE)    // @Builder 가 쓰는 생성자
public class User extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password;
    private String nickname;
    private int goalCreatedCount;
    private boolean isPremium;

    /**
     * 회원가입으로 생성합니다. 가입 시점의 불변식(무료 시작, 목표 생성 0회)을 여기서 강제합니다.
     *
     * @param encodedPassword 반드시 인코딩된 비밀번호. 원문을 넘기지 마세요.
     */
    public static User create(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .goalCreatedCount(0)
                .isPremium(false)
                .build();
    }
}
```

- 팩토리 이름은 의도를 드러내게 씁니다: `create`, `of`, `from` 등.
- 상태 변경도 setter 대신 의도가 드러나는 메서드로 만듭니다.

  ```java
  user.markPremium();         // ✅   user.setIsPremium(true)        ❌
  user.increaseGoalCount();   // ✅   user.setGoalCreatedCount(n)    ❌
  ```
- 실제 예시: `domain/user/entity/User`, `domain/auth/entity/RefreshToken`

**5. 문제가 생기면 `traceId`로 찾습니다.** 모든 응답 본문과 `X-Trace-Id` 헤더에 실려 있고, 서버 로그도 이 값으로 검색됩니다.

## Frontend: API 타입 생성

백엔드를 띄운 상태에서 OpenAPI 스펙으로 타입을 뽑습니다.

```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```

API가 바뀌면 다시 실행해서 타입을 갱신하세요.

## Spring Boot 4 주의사항

Boot 3 기준 자료나 코드를 그대로 가져오면 깨지는 부분이 있습니다.

- **웹 스타터 이름**: `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- **Jackson 3**: 패키지가 `com.fasterxml.jackson.*` → `tools.jackson.*`로 바뀌었습니다. (`ObjectMapper` → `JsonMapper` 권장. 어노테이션 `com.fasterxml.jackson.annotation`은 그대로)
- **테스트 스타터가 모듈별로 분리됨**: `spring-boot-starter-test` 하나로 끝나지 않습니다. 쓰는 모듈마다 `-test` 스타터를 추가합니다.
  - 예: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`
- **테스트 슬라이스 어노테이션 패키지 변경**
  - `@WebMvcTest`, `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`
  - `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`
- **springdoc**: Boot 4는 `springdoc-openapi` 3.x를 써야 합니다. 2.x는 Boot 3 전용입니다.

---

# 협업 규칙

모노레포라 프론트·백엔드가 같은 이슈 번호와 같은 `develop` 브랜치를 공유합니다.
누가 어느 영역을 작업 중인지 한눈에 보이도록 아래 규칙을 지킵니다.

## 한눈에 보기

| 대상 | 형식 | 예 |
|---|---|---|
| 이슈 제목 | `[BE]` / `[FE]` / `[ALL]` + 설명 | `[BE] JWT 인증 구현` |
| 브랜치 | `type/번호-설명` (소문자) | `feat/12-jwt-auth` |
| PR 제목 / squash 커밋 | `Type(BE): 설명` | `Feat(BE): JWT 인증 구현` |
| 작업 중 커밋 | `Type: 설명` (scope 생략 가능) | `Feat: 토큰 발급 로직 추가` |
| 라벨 | 타입 + 영역 | `Feat` + `backend` |

- **Type**은 라벨과 똑같이 첫 글자 대문자로 씁니다: `Feat`, `Fix`, `Refactor`, `Chore`, `Docs`, `Test`
- **영역 scope**는 대문자 `BE` / `FE`로 씁니다. 양쪽 공통이면 괄호 없이 씁니다 (`Docs: 협업 규칙 문서화`).
- **브랜치만 소문자**로 씁니다. 리눅스는 대소문자를 구분하고 맥·윈도우는 구분하지 않아서, 대문자가 섞인 브랜치명은 팀원 OS가 다르면 사고가 납니다.
- 대괄호로 감싼 형식(`[Feat(BE)]: ...`)은 쓰지 않습니다. Conventional Commits의 `type(scope):` 패턴이 깨져서 commitlint 같은 도구나 `git log --grep "^Feat"` 검색이 먹지 않습니다.

## 이슈

**제목**

```
[BE] 투두 생성 API 구현
[FE] 로그인 화면 퍼블리싱
[ALL] 협업 규칙 문서화
```

영역 태그를 앞에 붙입니다. 이슈 번호가 프론트·백엔드 통합이라
제목만 보고 담당 영역을 구분할 수 있어야 합니다.

| 태그 | 영역 |
|---|---|
| `[BE]` | 백엔드 |
| `[FE]` | 프론트엔드 |
| `[ALL]` | 양쪽 공통 (루트 문서, `.github/` 등) |

- 제목 앞자리는 **영역** 태그 자리입니다. `[DOCS]`처럼 타입을 넣지 않습니다. 타입은 라벨로만 표시합니다.
- 공통 작업도 태그를 생략하지 않고 `[ALL]`을 붙입니다. 생략하면 공통 작업인지 태그를 깜빡한 건지 구분이 안 됩니다.

이슈 템플릿의 기본 제목은 비워뒀습니다. 영역 태그를 직접 붙여주세요.

| 템플릿 | 타입 |
|---|---|
| Feat Template | `Feat` (라벨 자동) |
| Bug Template | `Fix` (라벨 자동) |
| Task Template | `Refactor` / `Chore` / `Docs` / `Test` (타입 라벨 직접 선택) |

**라벨은 타입 + 영역 2개**

| 종류 | 라벨 | 용도 |
|---|---|---|
| 타입 | `Feat` | 새 기능 |
| | `Fix` | 버그 수정 |
| | `Refactor` | 기능 변화 없는 구조 개선 |
| | `Chore` | 빌드·설정·의존성 |
| | `Docs` | 문서 |
| | `Test` | 테스트 |
| 영역 | `backend` | 백엔드 작업 |
| | `frontend` | 프론트엔드 작업 |

예) `[BE] JWT 인증 구현` → `Feat` + `backend`

`[ALL]` 이슈는 영역 라벨을 생략하거나 `backend` + `frontend` 둘 다 붙입니다.
예) `[ALL] 협업 규칙 문서화` → `Docs` (또는 `Docs` + `backend` + `frontend`)

- `all` 같은 별도 영역 라벨은 만들지 않습니다. `label:backend`로 필터링할 때 공통 이슈가 빠져서 오히려 안 보이게 됩니다.
- 양쪽 필터에 모두 보여야 하는 공통 이슈라면 둘 다 붙이는 쪽을 고릅니다.

영역 라벨이 있으면 이슈 목록에서 `label:backend`로 필터링해 자기 작업만 볼 수 있습니다.

**Assignee를 지정합니다.** 비워두면 누가 잡고 있는지 알 수 없어 중복 작업이 생깁니다.

## 브랜치

```
<type>/<이슈번호>-<설명>
예) feat/12-todo-crud, chore/3-monorepo-restructure
```

- `main`: 배포되는 코드. 배포 시점에만 갱신
- `develop`: 통합. 작업 브랜치는 `develop`에서 따고 `develop`으로 PR
- 설명 부분은 영어 소문자 + 하이픈

**브랜치를 따기 전에 반드시 `develop`을 최신화합니다.**

```bash
git checkout develop
git pull
git checkout -b feat/12-todo-crud
```

모노레포에서는 프론트·백엔드 머지가 섞여 `develop`이 자주 움직입니다.
오래된 `develop`에서 브랜치를 따면 나중에 충돌이 납니다.

## 커밋

```
<Type>: <내용>
예) Feat: 토큰 발급 로직 추가
```

| Type | 용도 |
|---|---|
| `Feat` | 새 기능 |
| `Fix` | 버그 수정 |
| `Refactor` | 기능 변화 없는 구조 개선 |
| `Chore` | 빌드·설정·의존성 |
| `Docs` | 문서 |
| `Test` | 테스트 |

작업 중 커밋은 scope를 생략해도 됩니다. squash 머지되면 작업 중 커밋은 사라지고 PR 제목만 `develop`에 남습니다.

## Pull Request

- **대상 브랜치는 `develop`** (배포용 `develop` → `main` PR은 예외)
- **제목**: `Type(BE): 설명` / `Type(FE): 설명`, 공통이면 `Type: 설명`
  - 예) `Feat(BE): JWT 인증 구현`
  - squash 머지 시 **PR 제목이 그대로 `develop`의 커밋 메시지**가 됩니다. 라벨은 git에 남지 않으니 영역을 제목의 scope로 남깁니다.
- **본문에 `Closes #N`** — 머지될 때 이슈가 자동으로 닫힙니다
- **라벨**: 이슈와 동일하게 타입 + 영역 2개
- **Assignee** 지정
- **PR 템플릿 체크리스트**를 채웁니다

### 머지 방식

| 방향 | 방식 | 이유 |
|---|---|---|
| 작업 브랜치 → `develop` | **Squash** | 작업 중 커밋을 하나로 압축 |
| `develop` → `main` | **Merge commit** | 이번 배포에 들어간 기능들을 개별 커밋으로 남김 |

### 머지 조건

- `ci-backend` (이후 `ci-frontend`) 체크가 **초록불**이어야 합니다. 빨간불이면 머지하지 않습니다.
- 리뷰 승인 1명. 가능하면 같은 영역(BE/FE) 담당자가 봅니다.

## 모노레포에서 조심할 것

- **자기 영역 폴더만 수정합니다.** 백엔드는 `backend/`, 프론트는 `frontend/`.
- **루트 파일은 수정 전에 공유합니다.** `README.md`, `.gitignore`, `.github/` 는
  양쪽이 모두 쓰는 파일이라 동시에 건드리면 충돌이 납니다.
- 코드 충돌은 폴더가 갈려 있어 거의 없지만, 위 루트 파일들만 예외입니다.

## Frontend 초기 세팅 체크리스트

- [ ] `frontend/` 에 프로젝트 초기화
- [ ] `.github/workflows/ci-frontend.yml` 추가 (**`paths` 필터를 걸지 말 것**. 위 [CI](#ci) 참고)
- [ ] Vercel 연결 시 **Root Directory를 `frontend`로 지정** (기본값인 루트로 두면 빌드 실패)
- [ ] 룰셋 필수 체크에 `ci-frontend` 추가
