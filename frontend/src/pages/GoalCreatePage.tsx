import { useState } from 'react'
import { useNavigate } from 'react-router'
import icClose from '../assets/icons/ic_close.svg'
import icMascot from '../assets/icons/ic_mascot.svg'
import icSparkleMain from '../assets/icons/ic_sparkle_main.svg'
import NavigationBar from '../components/NavigationBar'
import { GoalStepCard, GoalStepInput, GoalStepValue } from '../features/goal/components/GoalStepCard'

/** 'YYYY.MM.DD' 형식인지 */
const DATE_PATTERN = /^\d{4}\.\d{2}\.\d{2}$/

const STEPS = [
  { title: 'AI와 함께할\n새로운 목표를 입력해주세요.', showHint: true },
  { title: 'AI와 함께할\n새로운 목표를 입력해주세요.', showHint: true },
  { title: '목표를 진행 할\n기간을 입력해주세요.', showHint: false },
] as const

/**
 * 목표 추가 - 입력 (Figma: 새로운 AI 목표-입력 전 > 새로운 AI 목표 / 현재상태 / 진행 기간)
 * - 화면 패딩 20·20·0·20, 머리말 ↔ 카드 간격 20, 카드 사이 간격 16
 * - 머리말: [마스코트 36 + 반짝이 24, 간격 8] ↔ 제목 간격 8, 제목 묶음 ↔ 안내 말풍선 간격 12
 *   - 제목: Heading/H1 + 그라데이션(Brand/Main → Main_40 → Main_60)
 *   - 안내 말풍선: 패딩 4·12 / 반경 12·12·12·2 / 배경 Brand/Main_40 / Body/B9 White/W1
 * - 아래 버튼 2개: 높이 40 / 반경 8 / 간격 8. 이전 = 테두리 Black/Gray20 + Body/B8 Bold Black/Gray60,
 *   다음 = 배경 Brand/Main(비활성 Black/Gray20) + Body/B7 SemiBold White/W1
 */
export default function GoalCreatePage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [goal, setGoal] = useState('')
  const [currentLevel, setCurrentLevel] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const canNext = [
    goal.trim().length > 0,
    currentLevel.trim().length > 0,
    DATE_PATTERN.test(startDate) && DATE_PATTERN.test(endDate) && startDate <= endDate,
  ][step]

  const handlePrev = () => {
    if (step === 0) navigate(-1)
    else setStep(step - 1)
  }

  const handleNext = () => {
    if (!canNext) return
    if (step < STEPS.length - 1) {
      setStep(step + 1)
      return
    }
    // TODO: 입력한 값으로 AI 목표 세션 시작 (POST /api/v1/goals/ai/sessions) → 입력 후 화면
  }

  const { title, showHint } = STEPS[step]

  return (
    <div className="flex min-h-dvh flex-col">
      <NavigationBar
        title="목표 추가"
        rightIcon={<img src={icClose} alt="" className="size-[24px]" />}
        rightLabel="닫기"
        onRightClick={() => navigate(-1)}
      />

      <main className="flex flex-1 flex-col gap-[20px] px-[20px] pt-[20px]">
        <div className="flex flex-col items-start gap-[12px]">
          <div className="flex flex-col gap-[8px]">
            <div className="flex items-center gap-[8px]">
              <img src={icMascot} alt="" className="size-[36px]" />
              <img src={icSparkleMain} alt="" className="size-[24px]" />
            </div>
            <h1 className="bg-[linear-gradient(94deg,var(--color-brand-main)_-4.11%,var(--color-brand-main-40)_36.8%,var(--color-brand-main-60)_54.09%)] bg-clip-text text-heading-h1 whitespace-pre-line text-transparent">
              {title}
            </h1>
          </div>
          {showHint && (
            <span className="rounded-[12px] rounded-bl-[2px] bg-brand-main-40 px-[12px] py-[4px] text-body-b9 text-white-w1">
              간단하게 작성해도 괜찮아요!
            </span>
          )}
        </div>

        <div className="flex flex-col gap-[16px]">
          <GoalStepCard label="목표">
            {step === 0 ? (
              <GoalStepInput value={goal} onChange={setGoal} placeholder="예: 3개월 안에 토익 850점 받고 싶어." autoFocus />
            ) : (
              <GoalStepValue>{goal}</GoalStepValue>
            )}
          </GoalStepCard>

          {step >= 1 && (
            <GoalStepCard label="현재 상태">
              {step === 1 ? (
                <GoalStepInput value={currentLevel} onChange={setCurrentLevel} placeholder="예: 현재 토익 600점" autoFocus />
              ) : (
                <GoalStepValue>{currentLevel}</GoalStepValue>
              )}
            </GoalStepCard>
          )}

          {step >= 2 && (
            <GoalStepCard label="진행할 기간">
              <GoalStepInput
                prefix="시작일"
                value={startDate}
                onChange={setStartDate}
                placeholder="예: 2026.08.08"
                inputMode="numeric"
                autoFocus
              />
              <GoalStepInput
                prefix="종료일"
                value={endDate}
                onChange={setEndDate}
                placeholder="예: 2026.11.08"
                inputMode="numeric"
              />
            </GoalStepCard>
          )}
        </div>
      </main>

      <div className="flex gap-[8px] px-[20px] pt-[20px] pb-[52px]">
        <button
          type="button"
          onClick={handlePrev}
          className="h-[40px] flex-1 rounded-[8px] border border-black-gray20 bg-white-w1 text-body-b8 font-bold text-black-gray60"
        >
          이전
        </button>
        <button
          type="button"
          onClick={handleNext}
          disabled={!canNext}
          className="h-[40px] flex-1 rounded-[8px] bg-brand-main text-body-b7 font-semibold text-white-w1 disabled:bg-black-gray20"
        >
          다음
        </button>
      </div>
    </div>
  )
}
