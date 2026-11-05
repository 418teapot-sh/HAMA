import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import icClose from '../assets/icons/ic_close.svg'
import NavigationBar from '../components/NavigationBar'
import { getApiError } from '../api/client'
import { createGoalFromInput, generatePlans, selectPlan, type PlanView } from '../features/goal/aiApi'
import { deleteGoal, getGoal, type GoalDetail } from '../features/goal/api'
import FixedScheduleStep from '../features/goal/components/FixedScheduleStep'
import { GoalStepCard, GoalStepInput, GoalStepValue } from '../features/goal/components/GoalStepCard'
import GoalHeader from '../features/goal/components/GoalHeader'
import PlanProposal from '../features/goal/components/PlanProposal'
import { loadMe } from '../features/auth/api'
import { useAuthStore } from '../features/auth/store'
import CreditShortageModal from '../features/payment/CreditShortageModal'
import { GOAL_TEXT_ERROR, isMeaningfulText } from '../features/goal/validation'
import type { Weekday } from '../features/goal/weekdays'
import { paths } from '../paths'
import { toDateKey } from '../utils/date'

/** 'YYYY.MM.DD' 형식인지 */
const DATE_PATTERN = /^\d{4}\.\d{2}\.\d{2}$/

/** 목표 기간 최대 일수 (시작일 포함, 백엔드 기준) */
const MAX_PERIOD_DAYS = 365

/** 'YYYY.MM.DD' 형식이고 달력에 있는 날짜인지 (2월 30일 등을 걸러냄) */
function isValidDate(value: string) {
  if (!DATE_PATTERN.test(value)) return false
  const [year, month, day] = value.split('.').map(Number)
  const date = new Date(year, month - 1, day)
  return date.getMonth() === month - 1 && date.getDate() === day
}

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
  if (!isValidDate(value)) return '예: 2026.08.08 처럼 입력해주세요.'
  return undefined
}

/** 시작일 포함 며칠짜리 기간인지. 둘 다 'YYYY.MM.DD' */
function periodDays(startDate: string, endDate: string) {
  const toUtc = (value: string) => {
    const [year, month, day] = value.split('.').map(Number)
    return Date.UTC(year, month - 1, day)
  }
  return (toUtc(endDate) - toUtc(startDate)) / 86_400_000 + 1
}

/** 기간 오류 문구. 백엔드 GOAL_INVALID_PERIOD · GOAL_PERIOD_TOO_LONG 과 같은 기준 */
function periodError(startDate: string, endDate: string) {
  if (endDate < startDate) return '종료일은 시작일 이후여야 해요.'
  if (toApiDate(endDate) < toDateKey(new Date())) return '종료일은 오늘 이후여야 해요.'
  if (periodDays(startDate, endDate) > MAX_PERIOD_DAYS) return '목표 기간은 최대 1년이에요.'
  return undefined
}

/**
 * 목표 추가 (Figma: 새로운 AI 목표-입력 전 · 입력 후 / 오류 · 일정 입력 및 AI 진행)
 * - 입력 4단계: 목표 → 현재 상태 → 진행 기간 → 고정 일정
 * - 화면 패딩 20·20·0·20, 머리말 ↔ 카드 간격 20, 카드 사이 간격 16
 *   - 안내 말풍선: 패딩 4·12 / 반경 12·12·12·2 / 배경 Brand/Main_40 / Body/B9 White/W1
 * - 마지막 "다음" → 목표 만들기(POST /goals) + A/B 플랜 생성 동안 진행 화면, 끝나면 완료 화면
 *   크레딧이 0이면(402) 크레딧 부족 팝업
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
  /** 만든 목표 id. 뒤 단계가 실패해 다시 시도해도 목표를 또 만들지 않도록 따로 둠 */
  const [createdGoalId, setCreatedGoalId] = useState<number | null>(null)
  const [createdGoal, setCreatedGoal] = useState<GoalDetail | null>(null)
  /** 서버가 거절한 기간 오류. 날짜를 고치면 지움 */
  const [serverPeriodError, setServerPeriodError] = useState<string>()
  /** 목표 · 현재 상태 · 고정 일정 칸 오류 (글자 수 검사, 서버 400 fields, AI 422). 그 칸을 고치면 지움 */
  const [goalError, setGoalError] = useState<string>()
  const [levelError, setLevelError] = useState<string>()
  const [scheduleError, setScheduleError] = useState<string>()
  const [plans, setPlans] = useState<PlanView[]>([])
  /** 플랜을 더 만들 수 있는 횟수 (목표당 3번) */
  const [remainingGenerations, setRemainingGenerations] = useState(0)
  const [creditShortage, setCreditShortage] = useState(false)
  const credits = useAuthStore((state) => state.user?.credits ?? 0)
  const [planIndex, setPlanIndex] = useState(0)
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)
  const [starting, setStarting] = useState(false)
  const [planError, setPlanError] = useState<string>()
  /** 닫기를 눌렀는지. 목표를 만드는 중에 닫으면 응답이 온 뒤 지우는 데 씁니다 */
  const closed = useRef(false)

  const startError = touched.start ? dateError('시작일', startDate) : undefined
  const datesValid = isValidDate(startDate) && isValidDate(endDate)
  const endError = touched.end
    ? (dateError('종료일', endDate) ?? (datesValid ? periodError(startDate, endDate) : undefined) ?? serverPeriodError)
    : undefined

  const periodValid = datesValid && !periodError(startDate, endDate)
  const canNext = [isMeaningfulText(goal.trim()), currentLevel.trim().length > 0, periodValid, true][step]
  const nextBlocked = phase === 'loading' || (phase === 'input' && !canNext)

  /** 입력을 고치러 돌아갈 때 먼저 만든 목표를 지움 (화면에 안 보이고, 새 값으로 다시 만듦) */
  const discardCreatedGoal = (goalId: number | null) => {
    if (goalId !== null) deleteGoal(goalId).catch(() => {})
    setCreatedGoalId(null)
    setCreatedGoal(null)
  }

  /** 서버 400 VALIDATION_FAILED 의 fields 를 칸 오류로 옮기고 첫 번째로 틀린 단계로 이동. 옮길 칸이 없으면 false */
  const showFieldErrors = (fields: Record<string, string>) => {
    const scheduleKey = Object.keys(fields).find((key) => key.startsWith('fixedSchedules'))
    const periodMessage = fields.startDate ?? fields.endDate
    setGoalError(fields.title)
    setLevelError(fields.currentStatus)
    setScheduleError(scheduleKey && fields[scheduleKey])
    if (periodMessage) {
      setServerPeriodError(periodMessage)
      setTouched({ start: true, end: true })
    }
    const firstStep = fields.title ? 0 : fields.currentStatus ? 1 : periodMessage ? 2 : scheduleKey ? 3 : null
    if (firstStep === null) return false
    setStep(firstStep)
    setPhase('input')
    return true
  }

  const submit = async () => {
    setPhase('loading')
    // 목표는 이미 만들었고 상세 조회나 플랜 생성만 실패했다면 목표를 다시 만들지 않음
    let goalId = createdGoalId
    try {
      goalId ??= await createGoalFromInput({
        goal: goal.trim(),
        currentLevel: currentLevel.trim(),
        startDate: toApiDate(startDate),
        endDate: toApiDate(endDate),
        fixedSchedules,
      })
      // 목표는 이미 만들었고 상세 조회나 플랜 생성만 실패했다면 목표를 다시 만들지 않음
      const goalId =
        createdGoalId ??
        (await createGoalFromInput({
          goal: goal.trim(),
          currentLevel: currentLevel.trim(),
          startDate: toApiDate(startDate),
          endDate: toApiDate(endDate),
          fixedSchedules,
        }))
      // 만드는 중에 닫았으면 쓰지 않을 목표라 지우고 멈춤 (화면은 이미 닫힘)
      if (closed.current) {
        deleteGoal(goalId).catch(() => {})
        return
      }
      setCreatedGoalId(goalId)
      setCreatedGoal(await getGoal(goalId))
      const planList = await generatePlans(goalId)
      setPlans(planList.plans)
      setRemainingGenerations(planList.remainingGenerations)
      setPhase('done')
    } catch (error) {
      const apiError = getApiError(error)
      const code = apiError?.code
      if (code === 'INSUFFICIENT_CREDITS') {
        loadMe()
        setPhase('input')
        setCreditShortage(true)
      } else if (code === 'GOAL_INVALID_PERIOD' || code === 'GOAL_PERIOD_TOO_LONG') {
        setServerPeriodError(
          code === 'GOAL_PERIOD_TOO_LONG' ? '목표 기간은 최대 1년이에요.' : '기간을 다시 확인해주세요.',
        )
        setTouched((prev) => ({ ...prev, end: true }))
        setStep(2)
        setPhase('input')
      } else if (code === 'VALIDATION_FAILED' && apiError?.fields && showFieldErrors(apiError.fields)) {
        // 목표를 만들 때 거절돼서 지울 목표가 없음
      } else if (apiError && (code === 'AI_PLAN_INVALID_INPUT' || code === 'AI_PLAN_NO_AVAILABLE_DAY')) {
        // 같은 입력으로 다시 시도해도 또 실패하므로, 만든 목표를 지우고 고칠 단계로 돌려보냄 (생성 횟수는 줄지 않음)
        discardCreatedGoal(goalId)
        if (code === 'AI_PLAN_INVALID_INPUT') {
          setGoalError(apiError.message)
          setStep(0)
        } else {
          setScheduleError(apiError.message)
          setStep(3)
        }
        setPhase('input')
      } else {
        setPhase('error')
      }
    }
  }

  /**
   * 닫기: 플랜을 골라 시작하기 전에 나가면 만들어 둔 목표(PLANNING)를 지움.
   * 지우지 않으면 화면 어디에도 안 보이는 목표가 서버에 남습니다. 플랜 시작 요청 중에는 닫지 않습니다.
   */
  const handleClose = () => {
    if (starting) return
    closed.current = true
    if (createdGoalId !== null) deleteGoal(createdGoalId).catch(() => {})
    navigate(-1)
  }

  const handlePrev = () => {
    // 입력을 고치러 돌아가면 새 값으로 목표를 다시 만듦. 먼저 만든 목표는 화면에 안 보여서 지움
    if (phase === 'error') {
      discardCreatedGoal(createdGoalId)
      setPhase('input')
    }
    else if (step === 0) handleClose()
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
      if (step === 0) setGoalError(GOAL_TEXT_ERROR)
      if (step === 2) setTouched({ start: true, end: true })
      return
    }
    if (step === 0) setGoalError(undefined)
    if (step < STEPS.length - 1) setStep(step + 1)
    else submit()
  }

  /** 플랜 다시 만들기: 같은 목표로 A/B 플랜을 새로 받음 (목표당 3번, 고르지 않은 기존 플랜은 바뀜) */
  const regeneratePlans = async () => {
    if (!createdGoal || remainingGenerations <= 0 || starting) return
    setPhase('loading')
    setSelectedPlanId(null)
    setPlanIndex(0)
    setPlanError(undefined)
    try {
      const planList = await generatePlans(createdGoal.goalId)
      setPlans(planList.plans)
      setRemainingGenerations(planList.remainingGenerations)
    } catch (error) {
      const apiError = getApiError(error)
      // 입력 · 횟수 · 요일 문제는 다시 시도해도 같으므로 서버 문구로 알려줌. 나머지는 잠시 뒤 다시 시도
      const guided = ['AI_PLAN_INVALID_INPUT', 'AI_PLAN_GENERATION_LIMIT', 'AI_PLAN_NO_AVAILABLE_DAY']
      setPlanError(
        apiError && guided.includes(apiError.code)
          ? apiError.message
          : '플랜을 다시 만들지 못했어요. 잠시 후 다시 시도해주세요.',
      )
    }
    setPhase('plans')
  }

  /** 진행하기: 고른 플랜으로 목표 시작 (투두가 캘린더에 들어감) → 홈 */
  const startPlan = async () => {
    if (selectedPlanId === null || starting) return
    setStarting(true)
    setPlanError(undefined)
    try {
      await selectPlan(selectedPlanId)
      loadMe()
      navigate(paths.home, { replace: true })
    } catch (error) {
      const code = getApiError(error)?.code
      if (code === 'INSUFFICIENT_CREDITS') {
        loadMe()
        setCreditShortage(true)
      } else if (code === 'AI_PLAN_OUTDATED') setPlanError('플랜이 오래됐어요. 플랜을 다시 만들어주세요.')
      else setPlanError('플랜을 시작하지 못했어요. 잠시 후 다시 시도해주세요.')
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
        onRightClick={handleClose}
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
            <FixedScheduleStep
              value={fixedSchedules}
              onChange={(value) => {
                setFixedSchedules(value)
                setScheduleError(undefined)
              }}
              error={scheduleError}
            />
          ) : (
            <div className="flex flex-col gap-[16px]">
              <GoalStepCard label="목표">
                {step === 0 ? (
                  <GoalStepInput
                    value={goal}
                    onChange={(value) => {
                      setGoal(value)
                      setGoalError(undefined)
                    }}
                    error={goalError}
                    placeholder="예: 3개월 안에 토익 850점 받고 싶어."
                    autoFocus
                  />
                ) : (
                  <GoalStepValue>{goal}</GoalStepValue>
                )}
              </GoalStepCard>

              {step >= 1 && (
                <GoalStepCard label="현재 상태">
                  {step === 1 ? (
                    <GoalStepInput
                      value={currentLevel}
                      onChange={(value) => {
                        setCurrentLevel(value)
                        setLevelError(undefined)
                      }}
                      error={levelError}
                      placeholder="예: 현재 토익 600점"
                      autoFocus
                    />
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
                    onChange={(value) => {
                      setStartDate(value)
                      setServerPeriodError(undefined)
                    }}
                    onBlur={() => setTouched((prev) => ({ ...prev, start: true }))}
                    error={startError}
                    placeholder="예: 2026.08.08"
                    inputMode="numeric"
                    autoFocus
                  />
                  <GoalStepInput
                    prefix="종료일"
                    value={endDate}
                    onChange={(value) => {
                      setEndDate(value)
                      setServerPeriodError(undefined)
                    }}
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
          <button
            type="button"
            onClick={regeneratePlans}
            disabled={remainingGenerations <= 0 || starting}
            className="text-body-b8 text-black-gray40 underline disabled:no-underline"
          >
            {remainingGenerations > 0 ? `플랜 다시 만들기 (${remainingGenerations}번 남음)` : '플랜을 더 만들 수 없어요'}
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
            // 기간 단계와 무언가 적은 목표 단계는 회색이어도 눌러서 어떤 칸이 틀렸는지 오류를 볼 수 있게 함
            disabled={
              phase === 'loading' ||
              (phase === 'input' && !canNext && step !== 2 && !(step === 0 && goal.trim().length > 0))
            }
            aria-disabled={nextBlocked}
            className={`h-[40px] flex-1 rounded-[8px] text-body-b7 font-semibold text-white-w1 ${
              nextBlocked ? 'bg-black-gray20' : 'bg-brand-main'
            }`}
          >
            {phase === 'error' ? '다시 시도' : '다음'}
          </button>
        </div>
      )}

      <CreditShortageModal open={creditShortage} credits={credits} onClose={() => setCreditShortage(false)} />
    </div>
  )
}
