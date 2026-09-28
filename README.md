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

- 각 앱은 자기 폴더 안에서 독립적으로 빌드·실행합니다. 루트에는 빌드 설정이 없습니다.
- CI는 앱별 워크플로우로 나눕니다 (`ci-backend.yml`, 이후 `ci-frontend.yml`).
- 필수 체크 워크플로우에는 `paths` 필터를 걸지 않습니다. 프론트만 바뀐 PR에서 `ci-backend`가 실행되지 않으면 필수 체크가 "대기 중"으로 남아 머지가 영구히 막힙니다. 경로 필터는 배포 워크플로우에만 겁니다.

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

> 아직 SecurityConfig가 없어서 지금은 401이 뜹니다. 다음 이슈(global 패키지)에서 열립니다.

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

## 컨벤션

### 브랜치

```
<type>/<이슈번호>-<설명>
예) feat/12-todo-crud, chore/3-monorepo-restructure
```

- `main`: 배포
- `develop`: 통합. 작업 브랜치는 `develop`에서 따고 `develop`으로 PR

### 커밋

```
<type>: <내용>
예) feat: 투두 생성 API 추가
```

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 구조 개선 |
| `chore` | 빌드·설정·의존성 |
| `docs` | 문서 |
| `test` | 테스트 |

이슈·PR은 `.github`의 템플릿을 따르고, 같은 이름의 라벨을 붙입니다.
