# HAMA

[![Deploy Backend](https://github.com/418teapot-sh/HAMA/actions/workflows/deploy-backend.yml/badge.svg?branch=main&event=push)](https://github.com/418teapot-sh/HAMA/actions/workflows/deploy-backend.yml)
[![Deploy Frontend](https://github.com/418teapot-sh/HAMA/actions/workflows/deploy-frontend.yml/badge.svg?branch=main&event=push)](https://github.com/418teapot-sh/HAMA/actions/workflows/deploy-frontend.yml)

목표를 적으면 AI가 할 일로 쪼개 주는 투두 서비스입니다. 프론트엔드와 백엔드를 한 레포에서 관리합니다.

| | |
|---|---|
| 서비스 | https://www.todohama.site |
| API | https://api.todohama.site |
| API 문서 | 로컬 http://localhost:8080/swagger-ui.html |

## 로컬 실행

```bash
# 백엔드 (JDK 21, Docker 필요)
cd backend
docker compose up -d
./gradlew bootRun

# 프론트엔드
cd frontend
npm install
npm run dev
```

## 문서

- [협업 규칙](CONTRIBUTING.md): 이슈·브랜치·커밋·PR 형식, 머지 조건
- [백엔드 개발 가이드](backend/README.md): 설정, DB 마이그레이션, 주의사항
- API 명세: 노션 "데모데이 관련 → API 형식 → API 명세서"
