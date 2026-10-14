import { useState } from 'react'
import { useNavigate } from 'react-router'
import icClose from '../assets/icons/ic_close.svg'
import icMascot from '../assets/icons/ic_mascot.svg'
import icSparkleMain from '../assets/icons/ic_sparkle_main.svg'
import NavigationBar from '../components/NavigationBar'
import { createGoalWithAi, generatePlans, selectPlan, type PlanView } from '../features/goal/aiApi'
import type { GoalDetail } from '../features/goal/api'
import FixedScheduleStep from '../features/goal/components/FixedScheduleStep'
import { GoalStepCard, GoalStepInput, GoalStepValue } from '../features/goal/components/GoalStepCard'
import PlanProposal from '../features/goal/components/PlanProposal'
import type { Weekday } from '../features/goal/weekdays'
import { paths } from '../paths'

/** 'YYYY.MM.DD' 형식인지 */
const DATE_PATTERN = /^\d{4}\.\d{2}\.\d{2}$/

/** 'YYYY.MM.DD' → 'YYYY-MM-DD' (API 형식) */
const toApiDate = (date: string) => date.replaceAll('.', '-')

const STEPS = [
  { title: 'AI와 함께할\n새로운 목표를 입력해주세요.', showHint: true },
  { title: 'AI와 함께할\n새로운 목표를 입력해주세요.', showHint: true },
  { title: '목표를 진행 할\n기간을 입력해주세요.', showHint: false },
  { title: '고정 일정을\n입력해주세요.', showHint: false },
] as const

/** 입력 단계가 끝난 뒤 화면: AI 진행 중 → 완료 (또는 실패) → 플랜 제안 */
type Phase = 'input' | 'loading' | 'done' | 'error' | 'plans'

/** 날짜 입력칸 오류 문구. 아직 건드리지 않았으면 표시하지 않음 */
function dateError(label: string, value: string) {
  if (!value) return `${label}을 입력해주세요.`
  if (!DATE_PATTERN.test(value)) return '예: 2026.08.08 처럼 입력해주세요.'
  return undefined
}

/** 머리말: [마스코트 36 + 반짝이 24, 간격 8] ↔ 제목 간격 8. 제목은 Heading/H1 + 그라데이션 */
function GoalHeader({ title, center = false }: { title: string; center?: boolean }) {
  return (
    <div className={`flex flex-col gap-[8px] ${center ? 'items-center text-center' : ''}`}>
      <div className="flex items-center gap-[8px]">
        <img src={icMascot} alt="" className="size-[36px]" />
        <img src={icSparkleMain} alt="" className="size-[24px]" />
      </div>
      <h1 className="bg-[linear-gradient(94deg,var(--color-brand-main)_-4.11%,var(--color-brand-main-40)_36.8%,var(--color-brand-main-60)_54.09%)] bg-clip-text text-heading-h1 whitespace-pre-line text-transparent">
        {title}
      </h1>
    </div>
  )
}

/**
 * 목표 추가 (Figma: 새로운 AI 목표-입력 전 · 입력 후 / 오류 · 일정 입력 및 AI 진행)
 * - 입력 4단계: 목표 → 현재 상태 → 진행 기간 → 고정 일정
 * - 화면 패딩 20·20·0·20, 머리말 ↔ 카드 간격 20, 카드 사이 간격 16
 *   - 안내 말풍선: 패딩 4·12 / 반경 12·12·12·2 / 배경 Brand/Main_40 / Body/B9 White/W1
 * - 마지막 "다음" → AI 목표 만들기 (aiApi.createGoalWithAi) 동안 진행 화면, 끝나면 완료 화면
 * - 완료 "다음" → 플랜 제안 (A 여유형 / B 집중형). 고르고 "진행하기"를 누르면 목표 시작 → 홈
 *   - 플랜 제안 아래 버튼: 이전으로(테두리) · 진행하기(Brand/Main, 고르기 전 Black/Gray20) 높이 48 / 반경 12 / Body/B3,
 *     그 아래 "플랜 다시 만들기" Body/B8 Black/Gray40
 * - 아래 버튼 2개: 높이 40 / 반경 8 / 간격 8. 이전 = 테두리 Black/Gray20 + Body/B8 Bold Black/Gray60,
 *   다음 = 배경 Brand/Main(비활성 Black/Gray20) + Body/B7 SemiBold White/W1
 */
export default function GoalCreatePage() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [phase, setPhase] = useState<Phase>('input')
  const [goal, setGoal] = useState('')
  const [currentLevel, setCurrentLevel] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [touched, setTouched] = useState({ start: false, end: false })
  const [fixedSchedules, setFixedSchedules] = useState<Partial<Record<Weekday, string[]>>>({})
  const [createdGoal, setCreatedGoal] = useState<GoalDetail | null>(null)
  const [plans, setPlans] = useState<PlanView[]>([])
  const [planIndex, setPlanIndex] = useState(0)
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)
  const [starting, setStarting] = useState(false)
  const [planError, setPlanError] = useState<string>()

  const startError = touched.start ? dateError('시작일', startDate) : undefined
  const endError = touched.end
    ? (dateError('종료일', endDate) ??
      (DATE_PATTERN.test(startDate) && endDate < startDate ? '종료일은 시작일 이후여야 해요.' : undefined))
    : undefined

  const periodValid = DATE_PATTERN.test(startDate) && DATE_PATTERN.test(endDate) && startDate <= endDate
  const canNext = [goal.trim().length > 0, currentLevel.trim().length > 0, periodValid, true][step]
  const nextBlocked = phase === 'loading' || (phase === 'input' && !canNext)

  const submit = async () => {
    setPhase('loading')
    try {
      const result = await createGoalWithAi({
        goal: goal.trim(),
        currentLevel: currentLevel.trim(),
        startDate: toApiDate(startDate),
        endDate: toApiDate(endDate),
        fixedSchedules: Object.fromEntries(Object.entries(fixedSchedules)),
      })
      setCreatedGoal(result.goal)
      setPlans(result.plans)
      setPhase('done')
    } catch {
      setPhase('error')
    }
  }

  const handlePrev = () => {
    if (phase === 'error') setPhase('input')
    else if (step === 0) navigate(-1)
    else setStep(step - 1)
  }

  const handleNext = () => {
    if (phase === 'done') {
      setPhase('plans')
      return
    }
    if (phase === 'error') {
      submit()
      return
    }
    if (!canNext) {
      if (step === 2) setTouched({ start: true, end: true })
      return
    }
    if (step < STEPS.length - 1) setStep(step + 1)
    else submit()
  }

  /** 플랜 다시 만들기: 같은 목표로 A/B 플랜을 새로 받음 (고르지 않은 기존 플랜은 바뀜) */
  const regeneratePlans = async () => {
    if (!createdGoal) return
    setPhase('loading')
    setSelectedPlanId(null)
    setPlanIndex(0)
    try {
      setPlans(await generatePlans(createdGoal.goalId))
      setPhase('plans')
    } catch {
      setPlanError('플랜을 다시 만들지 못했어요. 잠시 후 다시 시도해주세요.')
      setPhase('plans')
    }
  }

  /** 진행하기: 고른 플랜으로 목표 시작 (투두가 캘린더에 들어감) → 홈 */
  const startPlan = async () => {
    if (selectedPlanId === null || starting) return
    setStarting(true)
    setPlanError(undefined)
    try {
      await selectPlan(selectedPlanId)
      navigate(paths.home, { replace: true })
    } catch {
      setPlanError('플랜을 시작하지 못했어요. 잠시 후 다시 시도해주세요.')
      setStarting(false)
    }
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

      {phase === 'input' ? (
        <main className="flex flex-1 flex-col gap-[20px] px-[20px] pt-[20px]">
          <div className="flex flex-col items-start gap-[12px]">
            <GoalHeader title={title} />
            {showHint && (
              <span className="rounded-[12px] rounded-bl-[2px] bg-brand-main-40 px-[12px] py-[4px] text-body-b9 text-white-w1">
                간단하게 작성해도 괜찮아요!
              </span>
            )}
          </div>

          {step === 3 ? (
            <FixedScheduleStep value={fixedSchedules} onChange={setFixedSchedules} />
          ) : (
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
                    onBlur={() => setTouched((prev) => ({ ...prev, start: true }))}
                    error={startError}
                    placeholder="예: 2026.08.08"
                    inputMode="numeric"
                    autoFocus
                  />
                  <GoalStepInput
                    prefix="종료일"
                    value={endDate}
                    onChange={setEndDate}
                    onBlur={() => setTouched((prev) => ({ ...prev, end: true }))}
                    error={endError}
                    placeholder="예: 2026.11.08"
                    inputMode="numeric"
                  />
                </GoalStepCard>
              )}
            </div>
          )}
        </main>
      ) : phase === 'plans' && createdGoal ? (
        <main className="flex flex-1 flex-col gap-[20px] px-[20px] pt-[20px]">
          <GoalHeader title={'AI가\n두가지 플랜을 제안해요'} />
          <PlanProposal
            goal={createdGoal}
            plans={plans}
            index={planIndex}
            onIndexChange={setPlanIndex}
            selectedPlanId={selectedPlanId}
            onSelect={setSelectedPlanId}
          />
        </main>
      ) : (
        <main className="flex flex-1 flex-col items-center justify-center gap-[8px] px-[20px]" aria-live="polite">
          {phase === 'loading' && (
            <>
              <GoalHeader title={'AI가 목표를 이루기 위한\n투두리스트를 만들고 있어요.'} center />
              <p className="text-body-b8 text-black-gray40">잠시만 기다려주세요.</p>
            </>
          )}
          {phase === 'done' && (
            <>
              <GoalHeader title={'AI가 목표를 이루기 위한\n투두리스트를 만들었어요.'} center />
              <p className="text-body-b8 text-black-gray40">다음을 눌러 플랜을 확인해주세요.</p>
            </>
          )}
          {phase === 'error' && (
            <>
              <GoalHeader title={'투두리스트를\n만들지 못했어요.'} center />
              <p className="text-body-b8 text-black-gray40">잠시 후 다시 시도해주세요.</p>
            </>
          )}
        </main>
      )}

      {phase === 'plans' ? (
        <div className="flex flex-col items-center gap-[8px] px-[20px] pt-[20px] pb-[40px]">
          {planError && (
            <p role="alert" className="text-body-b11 text-brand-eo">
              {planError}
            </p>
          )}
          <button
            type="button"
            onClick={() => setPhase('done')}
            className="h-[48px] w-full rounded-[12px] border border-black-gray20 bg-white-w1 text-body-b3 text-black-gray60"
          >
            이전으로
          </button>
          <button
            type="button"
            onClick={startPlan}
            disabled={selectedPlanId === null || starting}
            className="h-[48px] w-full rounded-[12px] bg-brand-main text-body-b3 text-white-w1 disabled:bg-black-gray20"
          >
            {starting ? '시작하는 중…' : '진행하기'}
          </button>
          <button type="button" onClick={regeneratePlans} className="text-body-b8 text-black-gray40 underline">
            플랜 다시 만들기
          </button>
        </div>
      ) : (
        <div className="flex gap-[8px] px-[20px] pt-[20px] pb-[52px]">
          <button
            type="button"
            onClick={handlePrev}
            disabled={phase === 'loading' || phase === 'done'}
            className="h-[40px] flex-1 rounded-[8px] border border-black-gray20 bg-white-w1 text-body-b8 font-bold text-black-gray60 disabled:text-black-gray20"
          >
            이전
          </button>
          <button
            type="button"
            onClick={handleNext}
            // 기간 단계는 회색이어도 눌러서 어떤 칸이 틀렸는지 오류를 볼 수 있게 함
            disabled={phase === 'loading' || (phase === 'input' && !canNext && step !== 2)}
            aria-disabled={nextBlocked}
            className={`h-[40px] flex-1 rounded-[8px] text-body-b7 font-semibold text-white-w1 ${
              nextBlocked ? 'bg-black-gray20' : 'bg-brand-main'
            }`}
          >
            {phase === 'error' ? '다시 시도' : '다음'}
          </button>
        </div>
      )}
    </div>
  )
}
