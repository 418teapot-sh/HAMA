import icBack from '../../../assets/icons/ic_back.svg'
import icCheck from '../../../assets/icons/ic_check.svg'
import icRightBlack from '../../../assets/icons/ic_right_black.svg'
import { formatGoalPeriod } from '../../../utils/date'
import type { PlanView } from '../aiApi'
import type { GoalDetail } from '../api'

type PlanProposalProps = {
  goal: GoalDetail
  plans: PlanView[]
  /** 지금 보고 있는 플랜 순서 (0 = A, 1 = B) */
  index: number
  onIndexChange: (index: number) => void
  /** 고른 플랜 id. 고르지 않았으면 null */
  selectedPlanId: number | null
  onSelect: (planId: number) => void
}

/** 체크 목록에 보여줄 마일스톤 수 */
const MAX_ITEMS = 3

/**
 * 플랜 제안 카드 넘기기 (Figma: 새로운 AI 목표- 플랜 제안)
 * - 맨 위 막대: PLAN A / PLAN B, 반경 100 / 배경 Brand/Main_40 / 글자 White/W1
 * - 카드 양옆 화살표로 A ↔ B. 끝이면 화살표를 흐리게
 * - 카드: 반경 20 / 패딩 20·16 / 간격 16. 고르면 배경 Card_Back_2(40%) + 테두리 Card_Stroke_2, 아니면 Card_Back + Stroke
 *   - 유형(여유형·집중형) Body/B9, 목표 제목 Heading/H1, 기간 Body/B8 Black/Gray60
 *   - 마일스톤 목록 상자: 홈 목표 요약 카드와 같은 모양 (체크 24 + Body/B7 SemiBold Brand/Main_80)
 *   - 선택 버튼: 높이 48 / 반경 12, 고르면 Brand/Main, 아니면 Black/Gray20
 */
export default function PlanProposal({
  goal,
  plans,
  index,
  onIndexChange,
  selectedPlanId,
  onSelect,
}: PlanProposalProps) {
  const plan = plans[index]
  if (!plan) return null

  const isSelected = plan.planId === selectedPlanId
  const isRelaxed = plan.variant === 'A'
  const hasPrev = index > 0
  const hasNext = index < plans.length - 1

  return (
    <div className="flex flex-col gap-[20px]">
      <p className="flex h-[24px] items-center justify-center rounded-full bg-brand-main-40 text-body-b9 text-white-w1">
        PLAN {plan.variant}
      </p>

      <div className="flex items-center gap-[4px]">
        <button
          type="button"
          aria-label="이전 플랜"
          disabled={!hasPrev}
          onClick={() => onIndexChange(index - 1)}
          className="shrink-0 disabled:opacity-30"
        >
          <img src={icBack} alt="" className="size-[24px]" />
        </button>

        <section
          className={`flex min-w-0 flex-1 flex-col gap-[16px] rounded-[20px] border px-[16px] py-[20px] shadow-card-shadow backdrop-blur-[10px] ${
            isSelected ? 'border-card-stroke-2 bg-card-back-2/40' : 'border-stroke bg-card-back'
          }`}
        >
          <div className="flex flex-col gap-[4px] pl-[12px]">
            <span className={`text-body-b9 ${isRelaxed ? 'text-brand-main-80' : 'text-brand-eo'}`}>{plan.title}</span>
            <h2 className="text-heading-h1 text-black-black">{goal.title}</h2>
            <p className="text-body-b8 text-black-gray60">{formatGoalPeriod(goal.startDate, goal.endDate)}</p>
          </div>

          {plan.milestones.length > 0 && (
            <ul className="flex flex-col gap-[4px] rounded-[16px] border border-stroke bg-card-back p-[16px]">
              {plan.milestones.slice(0, MAX_ITEMS).map((milestone) => (
                <li key={milestone.seq} className="flex items-center gap-[8px]">
                  <img src={icCheck} alt="" className="size-[24px] shrink-0" />
                  <span className="text-body-b7 font-semibold text-brand-main-80">{milestone.title}</span>
                </li>
              ))}
            </ul>
          )}

          <p className="pl-[12px] text-body-b8 text-black-gray60">{plan.summary}</p>

          <button
            type="button"
            aria-pressed={isSelected}
            onClick={() => onSelect(plan.planId)}
            className={`h-[48px] w-full rounded-[12px] text-body-b3 text-white-w1 ${
              isSelected ? 'bg-brand-main' : 'bg-black-gray20'
            }`}
          >
            선택
          </button>
        </section>

        <button
          type="button"
          aria-label="다음 플랜"
          disabled={!hasNext}
          onClick={() => onIndexChange(index + 1)}
          className="shrink-0 disabled:opacity-30"
        >
          <img src={icRightBlack} alt="" className="size-[24px]" />
        </button>
      </div>
    </div>
  )
}
