# 협업 규칙

| 대상 | 형식 | 예 |
|---|---|---|
| 이슈 제목 | `[BE]` / `[FE]` / `[ALL]` + 설명 | `[BE] JWT 인증 구현` |
| 라벨 | 타입 + 영역 | `Feat` + `backend` |
| 브랜치 | `type/이슈번호-설명` (소문자) | `feat/12-jwt-auth` |
| 커밋 | `Type: 설명` | `Feat: 토큰 발급 로직 추가` |
| PR 제목 | `Type(BE\|FE): 설명`, 공통이면 `Type: 설명` | `Feat(BE): JWT 인증 구현` |

Type: `Feat` `Fix` `Refactor` `Chore` `Docs` `Test`

## 작업 순서

1. 이슈를 만듭니다(템플릿 선택, 라벨·Assignee 지정).
2. 최신 `develop`에서 이슈 번호로 브랜치를 땁니다.
3. `develop`으로 PR을 올리고 본문에 `close #이슈번호`를 적습니다.

## 머지

| 방향 | 방식 | 승인 | 필수 체크 |
|---|---|---|---|
| 작업 브랜치 → `develop` | Squash | 0명 | `ci-backend` `ci-frontend` `pr-title` |
| `develop` → `main` (자동 배포) | Merge commit | 1명 | 위 3개 + `main-source` |

- `ci-backend`는 백엔드 테스트를 **전부** 돌립니다. 내 코드가 아니어도 깨지면 머지가 막힙니다.
- 자기 영역 폴더(`backend/`, `frontend/`)만 고칩니다. 루트 파일(`README.md`, `.github/` 등)은 고치기 전에 공유합니다.
- 필수 체크 워크플로에는 `paths` 필터를 걸지 않습니다. 걸면 체크가 "대기 중"으로 남아 머지가 막힙니다.
