import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { restoreSession } from '../auth/api'
import { useAuthStore } from '../auth/store'
import { paths } from '../../paths'
import LandingButton from './LandingButton'

/** 모바일 버튼 · 안내 문구 사이 간격과 안내 문구 글자 (데스크톱은 둘 다 사이 12 · 18px 행간 140%(25px)) */
const MOBILE = {
  // 01 · 시작하기: 사이 5 / 13px 행간 155%(20px)
  hero: { gap: 'gap-[5px]', note: 'text-[13px] leading-[20px]' },
  // 04 · 요금제 아래: 사이 8 / Body/B8 14px 행간 150%(21px)
  pricing: { gap: 'gap-[8px]', note: 'text-[14px] leading-[21px]' },
}

/**
 * 랜딩 버튼 + "첫 1회는 무료 | 자동 결제 없음" (Figma 01 · 시작하기 / 04 · 요금제 아래)
 * 버튼은 모바일 너비 가득 · 데스크톱 hero 크기, 안내 문구 400 가운데 Black/Gray60
 * - hero(첫 화면): "회원가입하고 시작하기" → 회원가입 화면. 이미 로그인한 상태라면 문구가 "홈으로 가기"가 되고 홈으로 갑니다 (디자이너 답변).
 *   랜딩으로 바로 들어오면 로그인 상태를 아직 모르므로, 처음에 refreshToken 쿠키로 한 번 확인합니다.
 * - pricing(요금제 아래): "결제하기" → 페이지 맨 위(첫 화면의 회원가입하고 시작하기)로 스크롤
 *   (Figma 온보딩 '프로토 타입' 프레임 · 프로토타입 영상, 디자이너 제안. 모바일도 같은 동작)
 * 데스크톱 버튼 · 안내 문구 사이 12 (프로토 타입 프레임. 이전 디자인은 8)
 */
export default function SignupCta({ place }: { place: keyof typeof MOBILE }) {
  const mobile = MOBILE[place]
  const navigate = useNavigate()
  const isPricing = place === 'pricing'
  const loggedIn = useAuthStore((s) => s.status === 'authenticated')

  // 로그인한 상태면 첫 화면 버튼을 "홈으로 가기"로 보여 주려고, 처음에 한 번 로그인 상태를 확인합니다.
  useEffect(() => {
    if (!isPricing && useAuthStore.getState().status === 'unknown') void restoreSession()
  }, [isPricing])

  async function handleClick() {
    if (isPricing) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    if (useAuthStore.getState().status === 'unknown') await restoreSession()
    navigate(useAuthStore.getState().status === 'authenticated' ? paths.home : paths.signup)
  }

  return (
    <div className={`flex w-full flex-col items-center ${mobile.gap} desktop:gap-[12px]`}>
      <LandingButton onClick={() => void handleClick()}>
        {isPricing ? '결제하기' : loggedIn ? '홈으로 가기' : '회원가입하고 시작하기'}
      </LandingButton>
      <p className={`text-center text-black-gray60 ${mobile.note} desktop:text-[18px] desktop:leading-[25px]`}>
        첫 1회는 무료 | 자동 결제 없음
      </p>
    </div>
  )
}
