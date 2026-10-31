import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import icBack from '../assets/icons/ic_back.svg'
import IconAlert from '../components/IconAlert'
import NavigationBar from '../components/NavigationBar'
import { CheckIcon, CloseIcon } from '../components/TodoIcons'
import { loadMe } from '../features/auth/api'
import { useAuthStore } from '../features/auth/store'
import { isPaddleConfigured, openCheckout } from '../features/payment/paddle'
import PlanOption from '../features/payment/PlanOption'
import { CREDIT_PLANS, DEFAULT_PLAN, formatPrice, type CreditPlan } from '../features/payment/plans'
import { paths } from '../paths'

type Result = 'success' | 'fail' | null

/**
 * 크레딧 충전 화면 (Figma UI > 결제: 충전 · 5회 선택 / 충전 성공 / 충전 실패)
 *
 * - NavigationBar: 뒤로 가기 + "크레딧 충전"
 * - 충전 내용 (패딩 20 · 간격 16)
 *   소개 (간격 4): "필요한 만큼 충전하세요" 24px 700 행간 150% Brand/Main / 안내 Body/B8 Black/Gray60
 *   남은 크레딧 (높이 48 · 패딩 0·16 · 반경 12 · 배경 Card_Back_2 40% · 테두리 Card_Stroke_2 · Card_Shadow · 배경 흐림 10)
 *   요금제 카드 3개 (간격 12, 기본 선택 추천 5회)
 *   이용 안내 (간격 2): Caption/Cap3 Black/Gray60 두 줄
 * - 결제 행동 영역 (화면 아래 고정, 패딩 12·20 · 간격 8 · 배경 White/W1)
 *   요약 "5회 충전" Body/B8 Brand/Main · 금액 Body/B3 / 버튼 높이 48 반경 12 Brand/Main Body/B3 / 안내 Caption/Cap3 가운데
 *   (Figma 영역 아래 끝 845 ~ 화면 아래 874 의 29px 는 Home Bar 자리라 아래 여백으로 비워 둠)
 * - 결제 결과 팝업 (공용 IconAlert)
 *   성공: 남은 크레딧 반영 후 표시 · 닫기 → 이전 작업(들어온 화면)으로 돌아감
 *   실패: 닫기 / 같은 요금제로 다시 시도
 *
 * 결제 (features/payment/paddle): 결제하기 → Paddle 결제창(overlay) → 결과
 *   - 결제 완료: 백엔드 웹훅이 크레딧을 충전할 때까지 /users/me 를 잠깐 다시 불러온 뒤 성공 팝업
 *   - 결제 실패 · 결제창을 못 엶: 실패 팝업 (다시 시도하기 = 같은 요금제로 다시)
 *   - 결제하지 않고 닫음: 아무것도 띄우지 않음
 * TODO: Paddle 값(client token · 가격 ID)은 환경변수로 받습니다. 값이 없으면 결제창 대신 실패 팝업이 뜹니다.
 * 개발 서버에서만 주소 뒤에 ?preview=success · ?preview=fail 을 붙이면 결과 팝업을 미리 볼 수 있습니다.
 */
export default function ChargePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const user = useAuthStore((s) => s.user)
  const credits = user?.credits ?? 0
  const [plan, setPlan] = useState<CreditPlan>(DEFAULT_PLAN)
  /** 결제창을 연 요금제 (성공 팝업 문구 · 다시 시도하기에 씀) */
  const [paidPlan, setPaidPlan] = useState<CreditPlan>(DEFAULT_PLAN)
  /** 결제창을 여는 중 · 결제 확인 중에는 버튼을 다시 누를 수 없음 */
  const [paying, setPaying] = useState(false)
  const [result, setResult] = useState<Result>(() => {
    const preview = import.meta.env.DEV ? searchParams.get('preview') : null
    return preview === 'success' || preview === 'fail' ? preview : null
  })

  // 들어올 때 남은 크레딧을 새로 불러옵니다. 옛 숫자를 기준으로 결제 뒤 충전 확인을 하면 틀린 숫자가 보입니다.
  useEffect(() => {
    void loadMe()
  }, [])

  /** 들어온 화면 (크레딧 부족 팝업 · 마이페이지 등이 state.from 으로 넘김). 없으면 뒤로, 그것도 없으면 홈 */
  function goBack() {
    const from = (location.state as { from?: string } | null)?.from
    if (from) navigate(from, { replace: true })
    else if (window.history.length > 1) navigate(-1)
    else navigate(paths.home, { replace: true })
  }

  async function handlePay(target: CreditPlan = plan) {
    if (paying || !user) return
    setPaidPlan(target)
    if (!isPaddleConfigured(target)) {
      setResult('fail')
      return
    }
    setPaying(true)
    try {
      const outcome = await openCheckout(target, user)
      if (outcome === 'completed') {
        await waitForCredits(credits)
        setResult('success')
      } else if (outcome === 'failed') {
        setResult('fail')
      }
    } catch {
      setResult('fail')
    } finally {
      setPaying(false)
    }
  }

  return (
    <div className="flex w-full flex-1 flex-col pb-[158px]">
      <NavigationBar
        title="크레딧 충전"
        leftLabel="뒤로 가기"
        leftIcon={<img src={icBack} alt="" className="size-[24px]" />}
        onLeftClick={goBack}
      />

      <main className="flex w-full flex-col gap-[16px] p-[20px]">
        <div className="flex flex-col gap-[4px]">
          <h1 className="text-[24px] leading-[150%] font-bold text-brand-main">필요한 만큼 충전하세요</h1>
          <p className="text-body-b8 text-black-gray60">월 구독 없이, 사용할 횟수만큼 충전해요.</p>
        </div>

        <div className="flex h-[48px] w-full items-center justify-between gap-[12px] rounded-[12px] border border-card-stroke-2 bg-card-back-2/40 px-[16px] shadow-card-shadow backdrop-blur-[10px]">
          <span className="text-body-b7 font-semibold text-brand-main">남은 크레딧</span>
          {/* Figma 충전 · 5회 선택(10/26 갱신본): 0회는 Black/Gray60 (디자이너 답변).
              TODO: 1회 이상일 때 색은 Figma 에 없어 기존 Brand/EO 를 그대로 둠 · 확인 필요 */}
          <span className={`text-body-b7 font-semibold ${credits === 0 ? 'text-black-gray60' : 'text-brand-eo'}`}>{credits}회</span>
        </div>

        <div role="radiogroup" aria-label="충전할 횟수" className="flex flex-col gap-[12px]">
          {CREDIT_PLANS.map((item) => (
            <PlanOption key={item.credits} plan={item} selected={item.credits === plan.credits} onSelect={() => setPlan(item)} />
          ))}
        </div>

        <div className="flex flex-col gap-[2px] text-caption-cap3 text-black-gray60">
          <p>목표 생성·일정 재계산 시 각각 1회 차감돼요.</p>
          {/* Figma 는 "구매일부터 1년 유효" 이지만 약관(서비스 종료일까지 사용)에 맞춤 · 민서 확인 */}
          <p>일회성 결제 · 자동 결제 없음 · 서비스 종료일(2026년 11월 20일)까지 사용</p>
        </div>
      </main>

      <div className="fixed bottom-0 left-1/2 flex w-full max-w-[402px] -translate-x-1/2 flex-col gap-[8px] bg-white-w1 px-[20px] pt-[12px] pb-[41px]">
        <div className="flex items-center justify-between gap-[12px]">
          <span className="text-body-b8 text-brand-main">{plan.credits}회 충전</span>
          <span className="text-body-b3 text-black-black">{formatPrice(plan.price)}</span>
        </div>
        <button
          type="button"
          disabled={paying}
          onClick={() => void handlePay()}
          className="flex h-[48px] w-full items-center justify-center rounded-[12px] bg-brand-main px-[20px] py-[12px] text-body-b3 text-white-w1"
        >
          {formatPrice(plan.price)} 결제하기
        </button>
        <p className="text-center text-caption-cap3 text-black-gray60">최종 결제 금액은 결제창에서 확인해 주세요.</p>
      </div>

      <IconAlert
        open={result === 'success'}
        icon={<CheckIcon className="size-[24px]" />}
        title="충전이 완료 되었어요."
        lines={[`크레딧 ${paidPlan.credits}회가 충전됐어요.`, '이제 목표를 이어서 만들어 보세요.']}
        note={`남은 크레딧 ${credits}회`}
        confirmText="닫기"
        onConfirm={() => {
          setResult(null)
          goBack()
        }}
      />
      <IconAlert
        open={result === 'fail'}
        icon={<CloseIcon className="size-[24px]" />}
        title="결제를 완료하지 못했어요"
        lines={['크레딧이 충전되지 않았어요.', '잠시 후 다시 시도해주세요.']}
        cancelText="닫기"
        onCancel={() => setResult(null)}
        confirmText="다시 시도하기"
        onConfirm={() => {
          setResult(null)
          void handlePay(paidPlan)
        }}
      />
    </div>
  )
}

/** 웹훅이 처리될 때까지 기다리는 최대 시간 · 다시 불러오는 간격 */
const CREDIT_WAIT_MS = 10000
const CREDIT_POLL_MS = 1000

/**
 * 결제 완료 뒤 백엔드 웹훅이 크레딧을 충전할 때까지 /users/me 를 다시 불러옵니다.
 * 시간 안에 늘지 않아도 결제는 끝났으므로 그대로 성공 팝업을 띄웁니다(남은 크레딧은 나중에 반영).
 */
async function waitForCredits(before: number) {
  const until = Date.now() + CREDIT_WAIT_MS
  while (Date.now() < until) {
    await new Promise((resolve) => setTimeout(resolve, CREDIT_POLL_MS))
    await loadMe()
    if ((useAuthStore.getState().user?.credits ?? 0) > before) return
  }
}
