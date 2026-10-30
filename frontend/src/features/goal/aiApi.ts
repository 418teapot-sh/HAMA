import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { createGoal, type DayOfWeek } from './api'
import type { Weekday } from './weekdays'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */

/** 플랜의 반복 할 일. timesPerWeek 는 1주일에 하는 횟수(1~7), minutes 는 한 번에 걸리는 분 */
export interface PlanTask {
  content: string
  timesPerWeek: number
  minutes: number
}

export interface PlanView {
  planId: number
  /** 'A' | 'B' */
  variant: string
  /** 예: '여유형' */
  title: string
  summary: string
  tasks: PlanTask[]
  stats: { totalTodos: number; avgDailyMinutes: number }
  selected: boolean
}

export interface PlanList {
  /** 이 목표에서 플랜을 더 만들 수 있는 횟수 (목표당 3번). 0 이면 생성 요청은 429 */
  remainingGenerations: number
  plans: PlanView[]
}

const BASE = '/api/v1/goals/ai'

/** A(여유형) · B(집중형) 플랜 생성. 다시 만들면 고르지 않은 기존 플랜을 바꿉니다 */
export async function generatePlans(goalId: number) {
  const res = await api.post<ApiResponse<PlanList>>(`${BASE}/plans`, { goalId })
  return res.data.data
}

/**
 * 플랜 선택. 플랜대로 투두를 만들고 목표를 진행 중(IN_PROGRESS)으로 바꿉니다. 크레딧 1회를 씁니다.
 * 실패: 크레딧 0이면 402(INSUFFICIENT_CREDITS), 목표 기간이 바뀌었거나 플랜 시작일이 지났으면 409(AI_PLAN_OUTDATED)
 */
export async function selectPlan(planId: number) {
  await api.post(`${BASE}/plans/${planId}/select`)
}

const DAY_OF_WEEK: Record<Weekday, DayOfWeek> = {
  월: 'MONDAY',
  화: 'TUESDAY',
  수: 'WEDNESDAY',
  목: 'THURSDAY',
  금: 'FRIDAY',
  토: 'SATURDAY',
  일: 'SUNDAY',
}

/** 고정 일정 내용 최대 길이 (백엔드 100자) */
const MAX_SCHEDULE_LENGTH = 100

export interface GoalAiInput {
  goal: string
  currentLevel: string
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  /** 요일별 고정 일정. 예: { 월: ['한국 디자인사 회의'] } */
  fixedSchedules: Partial<Record<Weekday, string[]>>
}

/**
 * 백엔드는 요일마다 고정 일정을 하나만 받아서, 한 요일에 여러 개면 쉼표로 이어 하나로 보냅니다.
 * 일정이 없는 요일은 보내지 않습니다.
 */
function toFixedSchedules(fixedSchedules: GoalAiInput['fixedSchedules']) {
  return (Object.entries(fixedSchedules) as [Weekday, string[]][])
    .filter(([, titles]) => titles.length > 0)
    .map(([day, titles]) => ({
      dayOfWeek: DAY_OF_WEEK[day],
      content: titles.join(', ').slice(0, MAX_SCHEDULE_LENGTH),
    }))
}

/**
 * 목표 추가 화면에서 입력한 값으로 목표를 만들고 goalId 를 돌려줍니다 (PLANNING).
 * 상세 조회 · 플랜 생성은 따로 합니다. 그쪽만 실패했을 때 목표를 또 만들지 않도록 나눠 둡니다.
 */
export async function createGoalFromInput(input: GoalAiInput) {
  const { goalId } = await createGoal({
    title: input.goal,
    currentStatus: input.currentLevel || undefined,
    startDate: input.startDate,
    endDate: input.endDate,
    fixedSchedules: toFixedSchedules(input.fixedSchedules),
  })
  return goalId
}
