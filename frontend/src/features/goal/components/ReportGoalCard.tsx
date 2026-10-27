import { useState } from 'react'
import icCheck from '../../../assets/icons/ic_check.svg'
import icRightBlack from '../../../assets/icons/ic_right_black.svg'
import Button from '../../../components/Button'
import Character from '../../../components/Character'
import { MeatballIcon, SparkleIcon } from '../../../components/TodoIcons'
import { formatGoalPeriod } from '../../../utils/date'
import type { GoalSummary, GoalTree } from '../api'
import { getCurrentPeriodGoals } from '../tree'

type ReportGoalCardProps = {
  goal: GoalSummary
  tree: GoalTree | null
  onMore: () => void
  /** 진행 중인 목표만. 없으면 일정 재계산 버튼을 숨김 */
  onReplan?: () => void
  replanning?: boolean
}

/** 체크 목록에 보여줄 최대 줄 수 */
const MAX_ITEMS = 3

/** 'YYYY-MM-DD' → 'MM.DD' */
function toMonthDay(date: string) {
  const [, month, day] = date.split('-')
  return `${month}.${day}`
}

/**
 * 지난 목표의 성공 여부. 백엔드 resultStatus 가 아직 채워지지 않아
 * 값이 없으면 투두 완료율 80% 이상을 성공으로 봅니다.
 * TODO: resultStatus 값이 정해지면 그 값만 씁니다 (백엔드 확인 필요)
 */
function isSucceeded(goal: GoalSummary) {
  if (goal.resultStatus) return goal.resultStatus === 'SUCCESS'
  return goal.progressRate >= 80
}

/** 카드 안 흰 상자: 패딩 20 / 반경 16 / 배경 Card_Back / Card_Shadow / 테두리 1 */
const BOX = 'rounded-[16px] border bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]'

/**
 * 리포트 목표 카드 (Figma: 리포트)
 * - 카드: 362 너비 / 패딩 20·16 / 간격 16 / 반경 20 / 테두리 Card_Stroke_2 / 배경 Card_Back_2(40%) / Card_Shadow
 * - 위쪽 정보(왼쪽 여백 20, 간격 8)
 *   - 뱃지: 높이 19 / 좌우 패딩 20 / Body/B9 White/W1
 *     진행 중 = Brand/Main_40, 목표달성 성공 = 할일(빨강), 목표 달성 실패 = 일정(노랑)
 *   - 제목 Heading/H1 Black/Black + 오른쪽 더보기(세로 점 3개), 기간 Body/B8 Black/Gray60
 * - 체크 목록 상자 (테두리 Stroke): 홈 목표 요약과 같은 줄 모양
 * - 투두 완료율 상자 (테두리 #F5F5F5): 캐릭터 24 + Body/B7 할일(빨강)
 * - AI 요약 상자: 반짝이 + "진행상황 AI 요약" Body/B7 Brand/Main_80 + 접기 화살표,
 *   줄마다 날짜·내용 Body/B8 Brand/Main
 * - 일정 재계산하기: 공용 Button (높이 48 / 반경 12 / Brand/Main)
 */
export default function ReportGoalCard({ goal, tree, onMore, onReplan, replanning }: ReportGoalCardProps) {
  const [summaryOpen, setSummaryOpen] = useState(true)

  const isPast = goal.status === 'PAST'
  const succeeded = isPast && isSucceeded(goal)
  const milestones = tree?.milestones ?? []
  const currentItems = getCurrentPeriodGoals(tree)
  // 지난 목표는 '이번 기간' 이 없으므로 마일스톤 제목을 보여줍니다
  const items = (currentItems.length > 0 ? currentItems : milestones.map((m) => m.title)).slice(0, MAX_ITEMS)

  const badge = !isPast
    ? { label: '진행중', className: 'bg-brand-main-40' }
    : succeeded
      ? { label: '목표달성 성공', className: 'bg-category-task' }
      : { label: '목표 달성 실패', className: 'bg-category-schedule' }

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
          {items.map((item) => (
            <li key={item} className="flex items-center gap-[8px]">
              <img src={icCheck} alt="" className="size-[24px] shrink-0" />
              <span className="text-body-b7 font-semibold text-brand-main-80">{item}</span>
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

      {milestones.length > 0 && (
        <div className={`flex flex-col gap-[12px] border-stroke ${BOX}`}>
          <button
            type="button"
            aria-expanded={summaryOpen}
            onClick={() => setSummaryOpen((open) => !open)}
            className="flex items-center gap-[4px] text-left"
          >
            <SparkleIcon className="size-[24px] shrink-0 text-brand-main" />
            <span className="flex-1 text-body-b7 font-semibold text-brand-main-80">
              진행상황 AI 요약 (현재 상태 입력 기반)
            </span>
            <img
              src={icRightBlack}
              alt=""
              className={`size-[20px] shrink-0 transition-transform ${summaryOpen ? '-rotate-90' : 'rotate-90'}`}
            />
          </button>
          {summaryOpen && (
            <ul className="flex flex-col gap-[4px] pl-[28px]">
              {milestones.map((milestone) => (
                <li key={milestone.milestoneId} className="text-body-b8 text-brand-main">
                  {toMonthDay(milestone.endDate)} {milestone.title}
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
