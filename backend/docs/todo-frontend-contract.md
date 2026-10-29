# 투두·일정 프론트 연동 계약

인증·ApiResponse는 기존 계약을 유지합니다. 날짜/일시는 KST입니다. 이 문서는 #222 구현 기준이며 실제 사용은 해당 백엔드 배포 후 가능합니다. 팀 프론트 변경은 별도입니다.

## 분류와 생성

| 화면 | 생성 API | 서버 분류 | 완료 체크 |
|---|---|---|---|
| 할일 | `POST /api/v1/todos` | `category: TASK` | 가능 |
| 기타 | `POST /api/v1/todos` | `category: OTHER` | 가능 |
| AI 칸의 직접 추가 | `POST /api/v1/todos` | `category: AI_GOAL_TASK`, `goalId` 필수 | 가능 |
| 일정 | `POST /api/v1/calendar/schedules` | `type: PERSONAL` (고정은 FIXED) | 현재 없음 |

TASK/OTHER는 목표 ID를 받을 수 없습니다. OTHER는 일반 할일과 같은 수정·완료·취소·미루기·삭제 동작을 지원하며 목표 진행률에 포함되지 않습니다. 목표 투두 직접 추가는 진행 중 본인 목표의 기간 안 날짜를 받습니다.

일정은 시각이 있는 별도 자원입니다. 투두 category에 SCHEDULE을 보내지 않습니다. 기존 일정 CRUD·종일·반복과 AI 배치 회피 계약을 유지합니다.

```json
{
  "type": "PERSONAL",
  "title": "회의",
  "startAt": "2026-11-02T15:00:00",
  "endAt": "2026-11-02T16:00:00",
  "allDay": false,
  "repeatRule": null,
  "memo": null
}
```

일정 시작·종료는 필수이고 시작 < 종료입니다. 반복 지원/종일 종료 경계는 기존 OpenAPI를 따릅니다. 투두/기타의 선택 시간은 둘 다 지정하거나 둘 다 비워야 합니다.

## AI 생성 출처

`GET /api/v1/todos`의 `pending`, `completed` 각각에 다음 필드를 추가합니다.

| origin | aiGenerated | 의미·직접 편집/삭제 |
|---|---|---|
| MANUAL | false | 사용자가 추가. 미완료 내용·시간 편집 및 삭제 가능 |
| AI | true | AI 플랜 적용으로 생성. 내용·시간 편집과 삭제 409 |
| LEGACY_UNKNOWN | null | 이전 목표 투두. 출처를 추측하지 않고 기존 서버 권한 유지 |

`category === AI_GOAL_TASK`만으로 AI 생성 여부를 판단하면 안 됩니다. 수동 목표 투두도 같은 category이며 목표 완료율에 포함됩니다. `aiGenerated === true`인 경우만 새 AI 제한을 적용합니다. 요청의 origin/aiGenerated로 출처를 지정하거나 변경할 수 없습니다.

서버는 AI 생성 건의 PATCH 내용·시간 수정 및 DELETE를 `409 TODO_AI_EDIT_NOT_ALLOWED`로 거절합니다. 완료·완료 취소·상태 글 수정·개별/그날 미루기·목표 전체 재배치는 기존처럼 허용합니다. 목표 삭제/회원 탈퇴에 따른 연결 데이터 정리는 이 직접 편집 제한과 별개입니다.

## 완료·완료 취소

- 완료: `PATCH /api/v1/todos/{todoId}/complete`.
- 완료 취소: `PATCH /api/v1/todos/{todoId}/uncomplete`, 본문 없음.
- 취소 성공200: 기존 완료 응답 모양의 `data={todoId,status:"PENDING",completedAt:null,goalProgressRate}`.
- 이미 미완료인 투두 취소는 `409 TODO_NOT_COMPLETED`. 타인403, 없는/삭제 투두404, 인증 없음401.
- 메모는 보존하지만 취소 중에는 완료한 목표 투두 상태 이력에서 보이지 않습니다. 다시 완료하면 새 완료 시각의 이력으로 표시됩니다.
- 취소는 목표 자체의 COMPLETED/END/달성 여부를 되돌리지 않습니다. 목표 투두 완료율·진행 요약 입력은 갱신합니다. 이전 종료 리뷰 조회는 보존하며, 달라진 완료율로 명시 재생성하면 기존 변경 입력 과금 규칙을 따릅니다.
- **완료 상태 그대로** 내용·시간 수정/미루기/재완료는 기존 `409 TODO_ALREADY_COMPLETED`가 맞습니다. 직접 수정하려면 먼저 완료 취소합니다. AI 출처 제한은 취소 후에도 유지합니다. 완료 상태의 메모 수정과 수동 투두 삭제는 가능합니다.

취소 후에는 투두·캘린더·관련 목표 진행률/요약을 재조회합니다. 기존 completed 항목에는 시간·goalId 등 pending 전체 정보가 없으므로 클라이언트에서 단순 배열 이동만 하지 않습니다. 미완료 메모의 기존 조회 제한도 유지합니다.

## 카테고리 변경

### 할일 ↔ 기타

`PATCH /api/v1/todos/{todoId}`에 `{"category":"OTHER"}` 또는 `{"category":"TASK"}`를 보냅니다. 내용·시간 PATCH와 함께 보낼 수 있고 ID·날짜·메모·미룬 횟수는 유지합니다. 응답 data에 확정된 category가 추가됩니다. category 생략은 유지, null/미지원 값400. 목표 투두의 분류 변경은409이며 목표 연결을 분리하지 않습니다.

### 할일·기타 ↔ 개인 일정

`POST /api/v1/calendar/category-conversions`를 사용합니다. sourceType/targetType은 TASK, OTHER, PERSONAL이며 서로 달라야 합니다.

할일 → 일정 예시:

```json
{
  "sourceType": "TASK",
  "refId": 123,
  "targetType": "PERSONAL",
  "startAt": "2026-11-02T15:00:00",
  "endAt": "2026-11-02T16:00:00"
}
```

일정 → 기타 예시:

```json
{
  "sourceType": "PERSONAL",
  "refId": 456,
  "targetType": "OTHER",
  "todoDate": "2026-11-03",
  "startTime": null,
  "endTime": null
}
```

- 대상 PERSONAL은 startAt/endAt 필수, `allDay=false`·반복 없음으로 생성합니다. 내용은 기존 일정 제목 제한인 100자 이하여야 합니다. 잘라 저장하지 않고 초과하면400입니다.
- PERSONAL → TASK/OTHER는 todoDate 필수이며 startTime/endTime은 함께 입력하거나 함께 비웁니다. 원본 일시를 자동 추측하지 않으므로 전환 화면에서 대상 날짜·시간을 확인합니다. 새 투두는 PENDING/MANUAL, 미룬 횟수0입니다.
- 원래 내용/제목과 상태 메모/일정 메모를 옮깁니다. 원본 행은 소프트 삭제해 보존하고 대상 항목에 새 ID를 발급합니다. `type+refId`로 식별합니다(투두 ID와 일정 ID 숫자는 같을 수도 있습니다).
- 성공200: `data={sourceType,sourceRefId,type,refId}`. 후속 요청은 새 type/refId의 API를 사용하고 목록·캘린더를 재조회합니다.
- TASK↔OTHER는 이 전환 API로도 가능하며 이 경우 ID를 유지합니다. 날짜/시간 변경은 기존 PATCH/미루기 API를 사용합니다.
- 완료 투두는 완료 취소 후 전환합니다. 목표 연결 투두·FIXED·반복 일정 전환은 이번 범위에서 제외하며409로 보호합니다. 반복 일정 전체 수정/삭제는 기존 일정 API에서 가능합니다.
- 대상 검증 실패 시 원본은 그대로 유지합니다. 동시 전환은 원본 잠금으로 하나만 성공합니다. 교차 전환의 원본 재요청은404입니다. 통신 실패만 보고 POST를 자동 재전송하지 말고 캘린더를 재조회해 결과를 확인합니다.

## 캘린더·파일·연동 후 확인

- `GET /api/v1/calendar` 및 `/api/v1/calendar/export`의 types에 OTHER를 추가합니다. 생략하면 기존 종류와 OTHER 전체입니다. 할일 TASK 필터에 OTHER를 자동 합치지 않습니다.
- 시간 없는 OTHER는 종일 표시 및 하루 DATE 항목으로 ICS 내보내기 합니다. UID는 `hama-OTHER-{todoId}@hama.app`입니다.
- 시간이 있는 OTHER는 기존 시간이 있는 TASK처럼 AI 빈 시간 탐색에서 피합니다. 시간이 없는 OTHER는 하루 전체를 점유하지 않습니다.
- 366일·10,000개 상한, JSON 오류/ICS 파일 응답, 일정 반복·소유권은 유지합니다.
- 배포 후 OpenAPI 타입 재생성, OTHER 목록/뱃지 매핑, aiGenerated 판별, 완료 토글, 분류 전환의 날짜·시각 입력 및 새ID 처리, 400/403/404/409 안내를 연결합니다.
