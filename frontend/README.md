# HAMA Frontend

목표를 적으면 AI가 할 일로 쪼개 주는 투두 서비스 **하마**의 프론트엔드입니다.

| | |
|---|---|
| 서비스 | https://www.todohama.site |
| 디자인 | [Figma](https://www.figma.com/design/Rv5u31D4Tsiw4ujMYevVfM/Demo-day) |
| 협업 규칙 | [CONTRIBUTING.md](../CONTRIBUTING.md) |

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Framework | React 19 (Vite) |
| Language | TypeScript |
| Styling | Tailwind CSS 4, Pretendard |
| State & Data Fetching | Zustand, axios |
| Router | react-router |
| Lint | oxlint |
| 결제 | Paddle |
| 배포 | Vercel |

## 실행 방법

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
```

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 |
| `npm run build` | 타입 검사 + 배포용 빌드 |
| `npm run lint` | oxlint 검사 |
| `npm run preview` | 빌드 결과 미리보기 |

패키지 매니저는 **npm**(`package-lock.json`)만 씁니다. pnpm · yarn lock 파일은 올리지 않습니다.

### 개발 서버와 백엔드

`npm run dev` 에서는 Vite 프록시가 `/api` 요청을 배포 백엔드(`https://api.todohama.site`)로 넘겨줍니다.
브라우저 입장에서는 같은 주소라 CORS 설정이 필요 없고, refreshToken 쿠키도 그대로 오갑니다.

로컬 백엔드를 쓰려면 `frontend/.env.local` 에 적습니다.

```bash
VITE_API_PROXY_TARGET=http://localhost:8080
```

## 폴더 구조

```
src/
├── pages/            화면 1개 = 파일 1개 (Home, Todo, GoalCreate, Report, MyPage, Login …)
├── features/         기능별 코드
│   ├── auth/         로그인 · 회원가입 · 계정 찾기, 토큰 store, RequireAuth
│   ├── todo/         투두 API · 카테고리 · 수정 저장 로직, components/ (투두 카드 · 입력 · 더보기 시트)
│   ├── goal/         목표 API · AI 플랜 API · 기간 검사, components/ (단계 카드, 고정일정, 리포트 카드)
│   ├── calendar/     캘린더 조회 · .ics 내보내기 API, useMonthMarks, components/ (내보내기 팝업)
│   ├── payment/      크레딧 충전 (Paddle), 크레딧 부족 팝업
│   ├── landing/      랜딩 페이지 섹션
│   └── terms/        약관 · 개인정보처리방침
├── components/       여러 화면에서 쓰는 공용 UI (Button, InputField, Modal, BottomSheet, Calendar, Toast, TabBar, Icons …)
├── layouts/          MobileLayout, TabLayout (하단 탭 바)
├── api/              axios 인스턴스(client.ts) · 에러 처리(errors.ts) · 공통 응답 타입(types.ts)
├── hooks/            공용 훅 (useToast)
├── utils/            날짜 등 공용 함수
├── assets/           icons · logo · landing 이미지
└── index.css         Tailwind + 디자인 토큰 (색상 · 타이포그래피)
```

- 여러 화면에서 쓰는 UI → `components/`
- 한 기능에서만 쓰는 API · 로직 → `features/{기능}/`, 그 기능의 UI → `features/{기능}/components/`
- import 는 `@/` 경로를 씁니다. 예) `import Modal from '@/components/Modal'` (`@` = `src`)

## 인증 · API 연동

- 모든 요청은 `api/client.ts` 의 axios 인스턴스를 씁니다.
- accessToken 은 **메모리(Zustand)에만** 저장합니다. localStorage 에 두지 않습니다.
- refreshToken 은 HttpOnly 쿠키라서 요청에 `withCredentials: true` 를 켭니다.
- 401 응답이 오면 `/api/v1/auth/refresh` 로 재발급받고 원래 요청을 한 번 다시 보냅니다.
  - 여러 요청이 동시에 401 을 받아도 refresh 는 한 번만 보냅니다. (리프레시 토큰이 매번 교체되기 때문)
  - 리프레시 토큰이 무효(4xx)일 때만 로그아웃하고, 네트워크 오류 · 502 같은 일시 오류에는 로그인을 유지합니다.
- 앱 시작 시 refresh 를 한 번 호출해서 새로고침해도 로그인이 유지됩니다.
- 에러 분기는 응답의 `message` 가 아니라 `code` 로 합니다. (`api/errors.ts` 의 `getApiError()`)
- 토스트 문구는 `getErrorMessage(error, 코드별 문구)` 로 만듭니다. 칸별 검사 문구 → 화면 문구 → 서버 문구 → 기본 문구 순서
- API 타입은 백엔드 API 명세를 보고 `features/{기능}/api.ts` 에 직접 작성합니다.

## 브랜치 · 배포 흐름

```
작업 브랜치 ──PR──▶ develop ──PR──▶ main ──▶ todohama.site 자동 배포
             Squash            Merge commit
```

- PR 마다 Vercel 미리보기 주소가 댓글로 달립니다.
- PR 체크: `ci-frontend`(lint · build), `pr-title`(제목 형식)
- PR 제목 형식: `{Type}(FE): {설명}` (Type: Feat, Fix, Refactor, Chore, Docs, Test)

자세한 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md) 를 봅니다.

## 역할

| | 담당 |
| --- | --- |
| 조민서 | 라우터 · 레이아웃 · 하단 탭 바, axios 설정 · 로그인 상태, 로그인 · 회원가입 · 계정 찾기, 투두리스트, 랜딩, 크레딧 충전 |
| 이상빈 | 디자인 토큰, 공용 컴포넌트(버튼 · 입력창 · 바텀시트 · 모달 · 캘린더), 홈, 목표 만들기 · AI 목표 상태 입력, 목표 리포트, 마이페이지, 캘린더 내보내기 |
