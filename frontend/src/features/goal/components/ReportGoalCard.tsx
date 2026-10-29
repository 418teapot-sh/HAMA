import { useState } from 'react'
import icCheck from '../../../assets/icons/ic_check.svg'
import icRightBlack from '../../../assets/icons/ic_right_black.svg'
import Button from '../../../components/Button'
import Character from '../../../components/Character'
import { MeatballIcon, SparkleIcon } from '../../../components/TodoIcons'
import { formatGoalPeriod } from '../../../utils/date'
import type { GoalSummary, GoalTask } from '../api'

type ReportGoalCardProps = {
  goal: GoalSummary
  /** 선택한 플랜의 반복 할 일 (GET /goals/{id}/tasks). 못 불러왔으면 빈 배열 */
  tasks: GoalTask[]
  /**
   * AI 요약 줄들. 진행 중 = 진행 요약(progress-summary), 끝난 목표 = 종료 리뷰(성과 비교 · 칭찬 두 줄).
   * null 이면 요약 상자를 숨김
   */
  aiSummary: string[] | null
  onMore: () => void
  /** 진행 중인 목표만. 없으면 일정 재계산 버튼을 숨김 */
  onReplan?: () => void
  replanning?: boolean
}

/** 체크 목록에 보여줄 최대 줄 수 */
const MAX_ITEMS = 3

/** 끝난 목표(완료·기간 종료)의 뱃지. 종료 AI 리뷰를 만들어야 resultStatus 가 생김 */
function endedBadge(goal: GoalSummary) {
  if (goal.resultStatus === 'ACHIEVED') return { label: '목표달성 성공', className: 'bg-category-task' }
  if (goal.resultStatus === 'MISSED') return { label: '목표 달성 실패', className: 'bg-category-schedule' }
  return { label: goal.status === 'COMPLETED' ? '목표 완료' : '기간 종료', className: 'bg-brand-main-40' }
}

/** 카드 안 흰 상자: 패딩 20 / 반경 16 / 배경 Card_Back / Card_Shadow / 테두리 1 */
const BOX = 'rounded-[16px] border bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]'

/**
 * 리포트 목표 카드 (Figma: 리포트)
 * - 카드: 362 너비 / 패딩 20·16 / 간격 16 / 반경 20 / 테두리 Card_Stroke_2 / 배경 Card_Back_2(40%) / Card_Shadow
 * - 위쪽 정보(왼쪽 여백 20, 간격 8)
 *   - 뱃지: 높이 19 / 좌우 패딩 20 / Body/B9 White/W1
 *     진행 중 = Brand/Main_40, 목표달성 성공(ACHIEVED) = 할일(빨강), 목표 달성 실패(MISSED) = 일정(노랑),
 *     리뷰 전 끝난 목표 = Brand/Main_40 '목표 완료' · '기간 종료'
 *   - 제목 Heading/H1 Black/Black + 오른쪽 더보기(세로 점 3개), 기간 Body/B8 Black/Gray60
 * - 체크 목록 상자 (테두리 Stroke): 홈 목표 요약과 같은 줄 모양
 * - 투두 완료율 상자 (테두리 #F5F5F5): 캐릭터 24 + Body/B7 할일(빨강)
 * - 체크 목록은 할 일마다 전체 투두 완료 수(완료/전체)를 붙임
 * - AI 요약 상자: 반짝이 + "진행상황 AI 요약" Body/B7 Brand/Main_80 + 접기 화살표, 줄마다 Body/B8 Brand/Main
 * - 일정 재계산하기: 공용 Button (높이 48 / 반경 12 / Brand/Main)
 */
export default function ReportGoalCard({ goal, tasks, aiSummary, onMore, onReplan, replanning }: ReportGoalCardProps) {
  const [summaryOpen, setSummaryOpen] = useState(true)

  const isEnded = goal.status === 'PAST' || goal.status === 'COMPLETED'
  const items = tasks.slice(0, MAX_ITEMS)
  const badge = isEnded ? endedBadge(goal) : { label: '진행중', className: 'bg-brand-main-40' }

  return (
    <section className="flex w-full flex-col gap-[16px] rounded-[20px] border border-card-stroke-2 bg-card-back-2/40 px-[16px] py-[20px] shadow-card-shadow backdrop-blur-[10px]">
      <div className="flex flex-col items-start gap-[8px] pl-[20px]">
        {/* TODO: 연속 진행 일수 API 가 생기면 '연속 n일째 진행중!' 으로 바꿉니다 */}
        <span
          className={`flex h-[19px] items-center rounded-[8px] rounded-bl-[2px] px-[20px] text-body-b9 text-white-w1 ${badge.className}`}
        >
          {badge.label}
        </span>
        <div className="flex w-full items-center justify-between">
          <h2 className="text-heading-h1 text-black-black">{goal.title}</h2>
          <button type="button" aria-label="더보기" onClick={onMore} className="shrink-0 text-black-gray60">
            <MeatballIcon className="size-[24px] rotate-90" />
          </button>
        </div>
        <p className="text-body-b8 text-black-gray60">{formatGoalPeriod(goal.startDate, goal.endDate)}</p>
      </div>

      {items.length > 0 && (
        <ul className={`flex flex-col gap-[4px] border-stroke ${BOX}`}>
          {items.map((task) => (
            <li key={task.content} className="flex items-center gap-[8px]">
              <img src={icCheck} alt="" className="size-[24px] shrink-0" />
              <span className="flex-1 text-body-b7 font-semibold text-brand-main-80">{task.content}</span>
              <span className="shrink-0 text-body-b9 text-black-gray60">
                {task.todoCompleted}/{task.todoTotal}
              </span>
            </li>
          ))}
        </ul>
      )}

      <p className={`flex items-center gap-[8px] border-[#f5f5f5] ${BOX}`}>
        <Character color="var(--color-category-task)" className="size-[24px] shrink-0" />
        <span className="text-body-b7 font-semibold text-category-task">
          투두 리스트 완료율 {Math.round(goal.progressRate)}%
        </span>
      </p>

      {aiSummary && aiSummary.length > 0 && (
        <div className={`flex flex-col gap-[12px] border-stroke ${BOX}`}>
          <button
            type="button"
            aria-expanded={summaryOpen}
            onClick={() => setSummaryOpen((open) => !open)}
            className="flex items-center gap-[4px] text-left"
          >
            <SparkleIcon className="size-[24px] shrink-0 text-brand-main" />
            <span className="flex-1 text-body-b7 font-semibold text-brand-main-80">
              {isEnded ? '목표 종료 AI 리뷰' : '진행상황 AI 요약 (현재 상태 입력 기반)'}
            </span>
            <img
              src={icRightBlack}
              alt=""
              className={`size-[20px] shrink-0 transition-transform ${summaryOpen ? '-rotate-90' : 'rotate-90'}`}
            />
          </button>
          {summaryOpen && (
            <ul className="flex flex-col gap-[4px] pl-[28px]">
              {aiSummary.map((line) => (
                <li key={line} className="text-body-b8 text-brand-main">
                  {line}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {onReplan && (
        <Button onClick={onReplan} disabled={replanning}>
          {replanning ? '재계산하는 중…' : '일정 재계산하기'}
        </Button>
      )}
    </section>
  )
}
