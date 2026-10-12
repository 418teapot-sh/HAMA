import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { getGoal } from './api'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */

/** AI 가 다음 답변으로 기대하는 정보 */
export type Expects = 'CURRENT_LEVEL' | 'PERIOD' | 'AVAILABLE_TIME' | 'OTHER'

/** COLLECTING(정보 수집 중) → READY(초안 완성) → CONFIRMED(목표 생성됨) */
export type SessionStatus = 'COLLECTING' | 'READY' | 'CONFIRMED'

interface AiMessage {
  messageId: number
  content: string
  expects: Expects | null
}

interface SessionStarted {
  sessionId: number
  status: SessionStatus
  aiMessage: AiMessage
}

interface SessionReply {
  status: SessionStatus
  aiMessage: AiMessage
  goalDraft: unknown | null
}

export interface PlanView {
  planId: number
  /** 'A' | 'B' */
  variant: string
  /** 예: '여유형' */
  title: string
  summary: string
  milestones: { seq: number; title: string; startDate: string; endDate: string }[]
  stats: { totalTodos: number; avgDailyMinutes: number }
  selected: boolean
}

const BASE = '/api/v1/goals/ai'

/** 목표 입력 (세션 시작). AI 가 첫 질문을 돌려줍니다 */
export async function startSession(rawGoal: string) {
  const res = await api.post<ApiResponse<SessionStarted>>(`${BASE}/sessions`, { rawGoal })
  return res.data.data
}

/** AI 질문에 답변. 정보가 다 모이면 status=READY */
export async function replySession(sessionId: number, content: string) {
  const res = await api.post<ApiResponse<SessionReply>>(`${BASE}/sessions/${sessionId}/messages`, { content })
  return res.data.data
}

/** 초안 수정. 바꿀 필드만 보냅니다 (날짜는 'YYYY-MM-DD') */
export async function updateDraft(
  sessionId: number,
  patch: { startDate?: string; endDate?: string; currentLevel?: string },
) {
  await api.patch(`${BASE}/sessions/${sessionId}/draft`, patch)
}

/** 목표 확정. 초안으로 PLANNING 목표를 만들고 goalId 를 돌려줍니다 */
export async function confirmSession(sessionId: number) {
  const res = await api.post<ApiResponse<{ goalId: number }>>(`${BASE}/sessions/${sessionId}/confirm`)
  return res.data.data.goalId
}

/** A(여유형) · B(집중형) 플랜 생성 */
export async function generatePlans(goalId: number) {
  const res = await api.post<ApiResponse<{ plans: PlanView[] }>>(`${BASE}/plans`, { goalId })
  return res.data.data.plans
}

/** 플랜 선택. 플랜대로 마일스톤·주간 목표·투두를 만들고 목표를 진행 중(IN_PROGRESS)으로 바꿉니다 */
export async function selectPlan(planId: number) {
  await api.post(`${BASE}/plans/${planId}/select`)
}

export interface GoalAiInput {
  goal: string
  currentLevel: string
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  /** 요일별 고정 일정. 예: { 월: ['한국 디자인사 회의'] } */
  fixedSchedules: Record<string, string[]>
}

/** 고정 일정을 AI 에게 보낼 문장으로 */
function describeFixedSchedules(fixedSchedules: Record<string, string[]>) {
  const days = Object.entries(fixedSchedules).filter(([, titles]) => titles.length > 0)
  if (days.length === 0) return '정해진 고정 일정은 없어요. 매일 시간을 낼 수 있어요.'
  const list = days.map(([day, titles]) => `${day}요일(${titles.join(', ')})`).join(', ')
  return `고정 일정은 ${list}이 있어요. 그 외 시간에 할 수 있어요.`
}

/** AI 가 기대하는 정보(expects)에 맞는 답을 사용자가 입력한 값으로 만듭니다 */
function answerFor(expects: Expects | null, input: GoalAiInput) {
  const period = `${input.startDate}부터 ${input.endDate}까지 할 거예요.`
  switch (expects) {
    case 'CURRENT_LEVEL':
      return input.currentLevel
    case 'PERIOD':
      return period
    case 'AVAILABLE_TIME':
      return describeFixedSchedules(input.fixedSchedules)
    default:
      return `현재 상태는 ${input.currentLevel}이고, ${period} ${describeFixedSchedules(input.fixedSchedules)}`
  }
}

/** AI 에게 답할 수 있는 최대 횟수 (백엔드는 첫 목표 포함 사용자 메시지 10개까지) */
const MAX_REPLIES = 8

/**
 * 목표 추가 화면에서 입력한 값으로 AI 목표를 만듭니다.
 * 세션 시작 → AI 질문에 자동 답변(READY 까지) → 기간·현재 상태를 입력값으로 맞춤 → 확정 → A/B 플랜 생성
 */
export async function createGoalWithAi(input: GoalAiInput) {
  const started = await startSession(input.goal)
  let { status, aiMessage } = started

  for (let i = 0; status !== 'READY' && i < MAX_REPLIES; i++) {
    const reply = await replySession(started.sessionId, answerFor(aiMessage.expects, input))
    status = reply.status
    aiMessage = reply.aiMessage
  }
  if (status !== 'READY') {
    throw new Error('AI 가 목표 초안을 완성하지 못했어요.')
  }

  // AI 가 정리한 초안의 기간·현재 상태를 사용자가 직접 입력한 값으로 확정합니다
  await updateDraft(started.sessionId, {
    startDate: input.startDate,
    endDate: input.endDate,
    currentLevel: input.currentLevel,
  })

  const goalId = await confirmSession(started.sessionId)
  const [goal, plans] = await Promise.all([getGoal(goalId), generatePlans(goalId)])
  return { goal, plans }
}
