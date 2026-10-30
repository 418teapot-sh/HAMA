import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { CalendarItem } from '../calendar/api'
import type { TodoCategoryKey } from './categories'
import type { TodoApiCategory, TodoView } from './types'

/**
 * 투두 API (/api/v1/todos) · 일정 API (/api/v1/calendar/schedules).
 * 요청·응답 형식은 Swagger 와 backend/docs/todo-frontend-contract.md (#223) 기준입니다.
 * - 할일 = TASK, 기타 = OTHER, AI = AI_GOAL_TASK(goalId 필수), 일정 = 캘린더 개인 일정(PERSONAL)
 */

/** 서버 출처: MANUAL(사용자) · AI(AI 플랜) · LEGACY_UNKNOWN(#223 이전 목표 투두) */
type TodoOrigin = 'MANUAL' | 'AI' | 'LEGACY_UNKNOWN'

/** 미완료 투두 */
interface PendingTodo {
  todoId: number
  category: TodoApiCategory
  content: string
  goalId: number | null
  goalTitle: string | null
  /** 'HH:mm' */
  startTime: string | null
  endTime: string | null
  postponedCount: number
  origin?: TodoOrigin
  /** true = AI 플랜으로 생성, false = 사용자, null = 출처 미확인 */
  aiGenerated?: boolean | null
  statusNote?: string | null
}

/** 완료 투두 */
interface CompletedTodo {
  todoId: number
  category: TodoApiCategory
  content: string
  /** 'yyyy-MM-ddTHH:mm:ss' */
  completedAt: string
  statusNote: string | null
  origin?: TodoOrigin
  aiGenerated?: boolean | null
}

interface DailyTodos {
  /** 'YYYY-MM-DD' */
  date: string
  pending: PendingTodo[]
  completed: CompletedTodo[]
}

const CATEGORY: Record<TodoApiCategory, TodoCategoryKey> = {
  TASK: 'task',
  OTHER: 'etc',
  AI_GOAL_TASK: 'ai',
}

/** 모르는 종류가 와도 화면이 깨지지 않게 할일로 보여 줍니다. */
function toCategory(category: string): TodoCategoryKey {
  return CATEGORY[category as TodoApiCategory] ?? 'task'
}

/** 'HH:mm:ss' 나 'HH:mm' → 'HH:mm' */
function toTime(value: string | null | undefined) {
  return value ? value.slice(0, 5) : undefined
}

/**
 * AI 가 만든 투두인지. 서버 aiGenerated 가 true 일 때만 막습니다 (계약 문서: category 로 추측하지 않음).
 * LEGACY_UNKNOWN(null) 은 서버가 기존 권한을 유지하므로 수정 가능으로 둡니다.
 */
function isAiGenerated(todo: { aiGenerated?: boolean | null }) {
  return todo.aiGenerated === true
}

/** 응답 → 화면 투두. 시간 있는 일정 · 투두는 시간순, 미완료 먼저 · 완료는 아래 (디자이너 답변) */
function toTodoViews({ date, pending, completed }: DailyTodos, schedules: CalendarItem[]): TodoView[] {
  const pendingViews: TodoView[] = [
    ...schedules.map(
      (item): TodoView => ({
        key: `schedule-${item.refId}`,
        kind: 'schedule',
        id: item.refId,
        date,
        title: item.title,
        category: 'schedule',
        scheduleType: item.type === 'FIXED' ? 'FIXED' : 'PERSONAL',
        done: false,
        time: item.allDay ? undefined : toTime(item.startTime),
      }),
    ),
    ...pending.map(
      (todo): TodoView => ({
        key: `todo-${todo.todoId}`,
        kind: 'todo',
        id: todo.todoId,
        date,
        title: todo.content,
        category: toCategory(todo.category),
        apiCategory: todo.category,
        done: false,
        time: toTime(todo.startTime),
        goalId: todo.goalId,
        aiGenerated: isAiGenerated(todo),
        statusNote: todo.statusNote ?? null,
      }),
    ),
  ]
  // 시간 있는 항목을 시간순으로 위에, 시간 없는 항목은 원래 순서대로 아래에 둡니다.
  const timed = pendingViews.filter((item) => item.time).sort((a, b) => a.time!.localeCompare(b.time!))
  const untimed = pendingViews.filter((item) => !item.time)

  return [
    ...timed,
    ...untimed,
    ...completed.map(
      (todo): TodoView => ({
        key: `todo-${todo.todoId}`,
        kind: 'todo',
        id: todo.todoId,
        date,
        title: todo.content,
        category: toCategory(todo.category),
        apiCategory: todo.category,
        done: true,
        aiGenerated: isAiGenerated(todo),
        statusNote: todo.statusNote,
      }),
    ),
  ]
}

/** 그 날의 개인 · 고정 일정 (GET /api/v1/calendar?types=FIXED,PERSONAL) */
async function getDailySchedules(date: string) {
  const res = await api.get<ApiResponse<{ items: CalendarItem[] }>>('/api/v1/calendar', {
    params: { from: date, to: date, types: 'FIXED,PERSONAL' },
  })
  return res.data.data.items.filter((item) => item.date === date)
}

/** 날짜별 투두 + 일정 (GET /api/v1/todos?date=YYYY-MM-DD). 일정 조회가 실패해도 투두는 보여 줍니다. */
export async function getDailyTodos(date: string) {
  const [todos, schedules] = await Promise.all([
    api.get<ApiResponse<DailyTodos>>('/api/v1/todos', { params: { date } }),
    getDailySchedules(date).catch(() => [] as CalendarItem[]),
  ])
  return toTodoViews(todos.data.data, schedules)
}

/** 할일 · 기타 추가 (POST /api/v1/todos). 시간은 화면에서 받지 않아 비웁니다. */
export async function createTodo(category: 'TASK' | 'OTHER', content: string, todoDate: string) {
  const res = await api.post<ApiResponse<{ todoId: number }>>('/api/v1/todos', { category, content, todoDate })
  return res.data.data.todoId
}

/**
 * AI 목표에 투두 직접 추가 (POST /api/v1/todos, AI_GOAL_TASK + goalId).
 * 진행 중인 본인 목표의 기간 안 날짜만 됩니다. 서버가 출처를 MANUAL 로 기록해 수정 · 삭제할 수 있습니다.
 */
export async function createGoalTodo(goalId: number, content: string, todoDate: string) {
  const res = await api.post<ApiResponse<{ todoId: number }>>('/api/v1/todos', {
    category: 'AI_GOAL_TASK',
    content,
    todoDate,
    goalId,
  })
  return res.data.data.todoId
}

/** 'YYYY-MM-DD' + 'HH:mm' → 'YYYY-MM-DDTHH:mm:00' */
function toDateTime(date: string, time: string) {
  return `${date}T${time}:00`
}

/** 개인 일정 추가 (POST /api/v1/calendar/schedules). 시작 < 종료, 제목 100자 이하 */
export async function createSchedule(title: string, date: string, startTime: string, endTime: string) {
  await api.post('/api/v1/calendar/schedules', {
    type: 'PERSONAL',
    title,
    startAt: toDateTime(date, startTime),
    endAt: toDateTime(date, endTime),
    allDay: false,
    repeatRule: null,
    memo: null,
  })
}

/** 일정 제목 수정 (PATCH /api/v1/calendar/schedules/{id}). 생략한 값은 유지 */
export async function updateScheduleTitle(scheduleId: number, title: string) {
  await api.patch(`/api/v1/calendar/schedules/${scheduleId}`, { title })
}

/** 일정 삭제 (DELETE /api/v1/calendar/schedules/{id}) */
export async function deleteSchedule(scheduleId: number) {
  await api.delete(`/api/v1/calendar/schedules/${scheduleId}`)
}

/**
 * 투두 수정 (PATCH /api/v1/todos/{id}). 생략한 값은 유지.
 * category 는 할일 ↔ 기타만 (목표 투두는 409). 완료한 투두는 409 TODO_ALREADY_COMPLETED,
 * AI 가 만든 투두는 409 TODO_AI_EDIT_NOT_ALLOWED
 */
export async function updateTodo(todoId: number, body: { content?: string; category?: 'TASK' | 'OTHER' }) {
  await api.patch(`/api/v1/todos/${todoId}`, body)
}

/**
 * 할일 · 기타 ↔ 개인 일정 전환 (POST /api/v1/calendar/category-conversions).
 * 원본은 지워지고 새 ID 가 생깁니다. 통신 실패해도 자동 재전송하지 않고 목록을 다시 불러옵니다.
 */
export async function convertCategory(
  body:
    | { sourceType: 'TASK' | 'OTHER'; refId: number; targetType: 'PERSONAL'; startAt: string; endAt: string }
    | { sourceType: 'PERSONAL'; refId: number; targetType: 'TASK' | 'OTHER'; todoDate: string; startTime: null; endTime: null },
) {
  const res = await api.post<ApiResponse<{ sourceType: string; sourceRefId: number; type: string; refId: number }>>(
    '/api/v1/calendar/category-conversions',
    body,
  )
  return res.data.data
}

export { toDateTime }

/** 완료 (PATCH /api/v1/todos/{id}/complete). 이미 완료면 409. */
export async function completeTodo(todoId: number) {
  await api.patch(`/api/v1/todos/${todoId}/complete`)
}

/** 완료 취소 (PATCH /api/v1/todos/{id}/uncomplete). 이미 미완료면 409 TODO_NOT_COMPLETED */
export async function uncompleteTodo(todoId: number) {
  await api.patch(`/api/v1/todos/${todoId}/uncomplete`)
}

/**
 * 현재 상태 메모 (PATCH /api/v1/todos/state/{id}). 미완료 · 완료 모두 가능, 빈 문자열이면 지움.
 * 목표 진행 요약 · 종료 AI 리뷰가 이 메모를 씁니다.
 */
export async function updateStatusNote(todoId: number, statusNote: string) {
  await api.patch(`/api/v1/todos/state/${todoId}`, { statusNote })
}

/**
 * 날짜 변경 (PATCH /api/v1/todos/{id}/postpone). 일반 할일은 원래 날짜보다 뒤의 날짜만 됩니다.
 * 완료한 투두는 409.
 */
export async function moveTodo(todoId: number, targetDate: string) {
  await api.patch(`/api/v1/todos/${todoId}/postpone`, { targetDate })
}

/** 삭제 (DELETE /api/v1/todos/{id}). AI 가 만든 투두는 409 */
export async function deleteTodo(todoId: number) {
  await api.delete(`/api/v1/todos/${todoId}`)
}
