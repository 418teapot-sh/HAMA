import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */
export type GoalStatus = 'PLANNING' | 'IN_PROGRESS' | 'PAST'

/** 목표 목록 항목 (GET /api/v1/goals) */
export interface GoalSummary {
  goalId: number
  title: string
  status: GoalStatus
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  progressRate: number
  resultStatus: string | null
}

interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

/** 기간 목표 (월/주 목표) */
export interface PeriodGoalNode {
  periodGoalId: number
  periodType: 'MONTHLY' | 'WEEKLY'
  title: string
  startDate: string
  endDate: string
  todoTotal: number
  todoCompleted: number
}

/** 마일스톤 */
export interface MilestoneNode {
  milestoneId: number
  seq: number
  title: string
  startDate: string
  endDate: string
  status: string
  progressRate: number
  periodGoals: PeriodGoalNode[]
}

/** 목표 계층 트리 (GET /api/v1/goals/{goalId}/tree). 플랜을 적용하지 않은 목표는 milestones 가 빈 배열 */
export interface GoalTree {
  goalId: number
  milestones: MilestoneNode[]
}

/** 진행 중인 목표 중 마감이 가장 가까운 하나. 없으면 null */
export async function getCurrentGoal() {
  const res = await api.get<ApiResponse<PageResponse<GoalSummary>>>('/api/v1/goals', {
    params: { status: 'IN_PROGRESS', page: 0, size: 1 },
  })
  return res.data.data.content[0] ?? null
}

export async function getGoalTree(goalId: number) {
  const res = await api.get<ApiResponse<GoalTree>>(`/api/v1/goals/${goalId}/tree`)
  return res.data.data
}
