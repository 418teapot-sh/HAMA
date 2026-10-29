import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */
/** PLANNING(플랜 선택 전) → IN_PROGRESS(진행 중) → COMPLETED(완료 처리) 또는 PAST(종료일 지남) */
export type GoalStatus = 'PLANNING' | 'IN_PROGRESS' | 'COMPLETED' | 'PAST'

/** 목표 목록 항목 (GET /api/v1/goals) */
export interface GoalSummary {
  goalId: number
  title: string
  status: GoalStatus
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  /** 완료한 AI 투두 ÷ 전체 AI 투두 × 100 (0~100) */
  progressRate: number
  /** 종료 AI 리뷰를 만든 뒤에만 값이 있음 (ACHIEVED 달성 / MISSED 미달성) */
  resultStatus: 'ACHIEVED' | 'MISSED' | null
  /** A/B 플랜을 선택해 투두가 생긴 목표인지 */
  aiPlanApplied: boolean
}

interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

/** 선택한 플랜의 반복 할 일 하나와 그 할 일로 만든 AI 투두 수. todo* 는 목표 전체, weekTodo* 는 이번 주 */
export interface GoalTask {
  content: string
  timesPerWeek: number
  minutes: number
  todoTotal: number
  todoCompleted: number
  weekTodoTotal: number
  weekTodoCompleted: number
}

/** 목표 할 일 목록 (GET /api/v1/goals/{goalId}/tasks). 예전 /tree 를 대신합니다. 플랜 선택 전이면 tasks 가 빈 배열 */
export interface GoalTasks {
  goalId: number
  /** 이번 주 월요일 'YYYY-MM-DD' */
  weekStart: string
  weekEnd: string
  tasks: GoalTask[]
}

/** 목표 상세 (GET /api/v1/goals/{goalId}) 중 화면에 쓰는 값 */
export interface GoalDetail {
  goalId: number
  title: string
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  status: GoalStatus
}

export async function getGoal(goalId: number) {
  const res = await api.get<ApiResponse<GoalDetail>>(`/api/v1/goals/${goalId}`)
  return res.data.data
}

/** 진행 중인 목표 중 마감이 가장 가까운 하나. 없으면 null */
export async function getCurrentGoal() {
  const res = await api.get<ApiResponse<PageResponse<GoalSummary>>>('/api/v1/goals', {
    params: { status: 'IN_PROGRESS', page: 0, size: 1 },
  })
  return res.data.data.content[0] ?? null
}

export async function getGoalTasks(goalId: number) {
  const res = await api.get<ApiResponse<GoalTasks>>(`/api/v1/goals/${goalId}/tasks`)
  return res.data.data
}

/** 목표 목록. IN_PROGRESS 는 마감 가까운 순, PAST 는 최근 종료 순, COMPLETED 는 최근 완료 순 */
export async function getGoals(status: 'IN_PROGRESS' | 'PAST' | 'COMPLETED', size = 20) {
  const res = await api.get<ApiResponse<PageResponse<GoalSummary>>>('/api/v1/goals', {
    params: { status, page: 0, size },
  })
  return res.data.data.content
}

/** 목표 삭제. 진행 중인 목표는 409(GOAL_NOT_DELETABLE) */
export async function deleteGoal(goalId: number) {
  await api.delete(`/api/v1/goals/${goalId}`)
}

/** 일정 재계산. 밀린·겹친 AI 투두를 오늘부터 빈 시간에 다시 배치합니다 */
export async function replanGoal(goalId: number) {
  const res = await api.post<ApiResponse<{ movedCount: number; unplacedCount: number }>>('/api/v1/goals/ai/replan', {
    goalId,
  })
  return res.data.data
}

/** 요일 (Java DayOfWeek) */
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface CreateGoalRequest {
  /** 100자 이하 */
  title: string
  /** 현재 상태 (200자 이하, 생략 가능) */
  currentStatus?: string
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD', 오늘 이후 */
  endDate: string
  /** 요일마다 하나씩 (내용 100자 이하). 플랜을 만들 때 그 요일에는 투두를 두지 않음 */
  fixedSchedules?: { dayOfWeek: DayOfWeek; content: string }[]
}

/**
 * 목표 만들기 (POST /api/v1/goals). PLANNING 으로 만들어지고, 플랜을 선택해야 투두가 생깁니다.
 * 실패: 크레딧 0이면 402(INSUFFICIENT_CREDITS), 기간 오류 400(GOAL_INVALID_PERIOD · GOAL_PERIOD_TOO_LONG)
 */
export async function createGoal(body: CreateGoalRequest) {
  const res = await api.post<ApiResponse<{ goalId: number; status: GoalStatus }>>('/api/v1/goals', body)
  return res.data.data
}

/**
 * 진행 중 AI 요약 (GET /api/v1/goals/{goalId}/progress-summary). 크레딧을 쓰지 않습니다.
 * status: READY(요약 있음) · EMPTY(상태 기록이 없음) · PENDING/GENERATING(만드는 중) · FAILED · INACTIVE(진행 중 아님)
 */
export interface ProgressSummary {
  goalId: number
  status: 'PENDING' | 'GENERATING' | 'READY' | 'EMPTY' | 'FAILED' | 'INACTIVE'
  summary: string | null
}

export async function getProgressSummary(goalId: number) {
  const res = await api.get<ApiResponse<ProgressSummary>>(`/api/v1/goals/${goalId}/progress-summary`)
  return res.data.data
}

/** 목표 종료 AI 리뷰. comparison(성과 비교) · message(칭찬/격려)는 각각 40자 이하 한 줄 */
export interface GoalReview {
  goalId: number
  achieved: boolean
  comparison: string
  message: string
}

/** 종료 AI 리뷰 조회 (GET /api/v1/goals/ai/reviews). 리뷰가 없으면 404(AI_REVIEW_NOT_FOUND) */
export async function getGoalReview(goalId: number) {
  const res = await api.get<ApiResponse<GoalReview>>('/api/v1/goals/ai/reviews', { params: { goalId } })
  return res.data.data
}
