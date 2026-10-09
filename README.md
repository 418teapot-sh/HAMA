# HAMA

목표를 적으면 AI가 할 일로 쪼개 주는 투두 서비스입니다. 프론트엔드와 백엔드를 한 레포에서 관리합니다.

- 서비스: https://www.todohama.site
- API: https://api.todohama.site (Swagger `/swagger-ui/index.html`)

```
HAMA/
├── backend/     # Spring Boot 4 (Java 21, Gradle, MySQL 8) → 개발 가이드: backend/README.md
├── frontend/    # Vite + React
└── .github/     # 이슈·PR 템플릿, CI·배포 워크플로
```

각 앱은 자기 폴더 안에서 따로 빌드하고 실행합니다. 루트에는 빌드 설정이 없습니다.

## 로컬 실행

```bash
# 백엔드 (JDK 21, Docker 필요)
cd backend
docker compose up -d      # MySQL (hama, hama_test DB)
./gradlew bootRun         # http://localhost:8080/swagger-ui.html

# 프론트엔드
cd frontend
npm install
npm run dev
```

백엔드 설정·테스트·문제 해결은 [backend/README.md](backend/README.md)를 보세요.

## API 응답 형식

모든 API는 아래 두 형식 중 하나로 응답합니다.

```jsonc
// 성공
{ "success": true,  "data": <T>,  "error": null, "traceId": "a1b2c3d4e5f6a7b8" }

// 실패
{ "success": false, "data": null, "error": { "code": "...", "message": "...", "fields": null }, "traceId": "..." }
```

- 에러 분기는 `message`가 아니라 **`error.code`** 로 합니다. 문구는 바뀔 수 있습니다.
- `error.fields`는 검증 실패(`VALIDATION_FAILED`)일 때만 `{ "필드명": "메시지" }`가 옵니다.
- 목록 API의 `data`는 `{ content, page, size, totalElements }`입니다.
- 문제가 생기면 `traceId`를 백엔드에 알려 주세요. 서버 로그를 이 값으로 찾습니다.
- 타입 생성: 백엔드를 띄운 상태에서 `npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts`

**인증**: 액세스 토큰은 응답 body(`data.accessToken`)로 오고, 요청 헤더 `Authorization: Bearer <토큰>`에 붙입니다(30분, 메모리에만 보관).
리프레시 토큰은 HttpOnly 쿠키로 오므로 인증 요청에는 `credentials: 'include'`(axios `withCredentials: true`)를 켭니다.
401 `UNAUTHORIZED`가 오면 `POST /api/auth/refresh`로 재발급한 뒤 다시 요청합니다.

---

# 협업 규칙

| 대상 | 형식 | 예 |
|---|---|---|
| 이슈 제목 | `[BE]` / `[FE]` / `[ALL]` + 설명 | `[BE] JWT 인증 구현` |
| 라벨 | 타입 + 영역 | `Feat` + `backend` |
| 브랜치 | `type/이슈번호-설명` (소문자) | `feat/12-jwt-auth` |
| 커밋 | `Type: 설명` | `Feat: 토큰 발급 로직 추가` |
| PR 제목 | `Type(BE\|FE): 설명`, 공통이면 `Type: 설명` | `Feat(BE): JWT 인증 구현` |

- **Type**: `Feat`, `Fix`, `Refactor`, `Chore`, `Docs`, `Test` (첫 글자 대문자, 라벨 이름과 같음)
- **영역 라벨**: `backend`, `frontend`. `[ALL]` 이슈는 생략하거나 둘 다 붙입니다.

## 작업 순서

1. **이슈를 먼저 만듭니다.** 템플릿(Feat / Bug / Task)을 고르고, 제목에 영역 태그를 붙이고, 라벨과 Assignee를 지정합니다.
2. **최신 `develop`에서 브랜치를 땁니다.** 브랜치 이름에 이슈 번호를 넣습니다.
   ```bash
   git checkout develop && git pull
   git checkout -b feat/12-todo-crud
   ```
   브랜치 이름은 소문자로만 씁니다. OS마다 대소문자 구분이 달라 사고가 납니다.
3. **`develop`으로 PR을 올립니다.**
   - 본문에 `close #이슈번호`를 적으면 머지할 때 이슈가 닫힙니다.
   - 라벨, Assignee, 템플릿 체크리스트를 채웁니다.
   - PR 제목이 그대로 `develop`의 커밋 메시지가 되므로 영역을 scope(`(BE)`)로 남깁니다. 형식은 `pr-title` 체크가 검사합니다.

## 머지

| 방향 | 방식 | 승인 | 필수 체크 |
|---|---|---|---|
| 작업 브랜치 → `develop` | **Squash** | 0명 | `ci-backend`, `ci-frontend`, `pr-title` |
| `develop` → `main` (배포) | **Merge commit** | 1명 | 위 3개 + `main-source` |

- 체크가 하나라도 빨간불이면 머지할 수 없습니다. `ci-backend`는 레포의 백엔드 테스트를 **전부** 돌리므로, 다른 사람 테스트가 깨져도 확인해야 합니다.
- `main`으로는 `develop`에서만 PR을 올릴 수 있습니다(`main-source`). `main`에 머지되면 자동 배포됩니다.

## 모노레포에서 조심할 것

- **자기 영역 폴더만 수정합니다.** 백엔드는 `backend/`, 프론트는 `frontend/`.
- **루트 파일은 수정 전에 공유합니다.** `README.md`, `.gitignore`, `.github/`는 양쪽이 같이 쓰는 파일입니다.
- **필수 체크 워크플로에는 `paths` 필터를 걸지 않습니다.** 프론트만 바뀐 PR에서 `ci-backend`가 안 돌면 "대기 중"으로 남아 머지가 영원히 막힙니다. 경로 필터는 배포 워크플로에만 겁니다.
- 워크플로의 job id가 곧 필수 체크 이름입니다. job id를 바꾸면 룰셋도 같이 바꿔야 합니다.
