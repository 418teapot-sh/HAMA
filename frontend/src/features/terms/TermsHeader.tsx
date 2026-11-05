import { useEffect } from 'react'
import { Link } from 'react-router'
import hippo from '../../assets/landing/hippo.webp'
import { paths } from '../../paths'
import { restoreSession } from '../auth/api'
import { useAuthStore } from '../auth/store'

/**
 * 이용약관 헤더 (Figma 헤더)
 * 모바일: 높이 72 / 패딩 16·20 / 로고 사이 8 · 하마 36 · HAMA 22px 900 행간 140%(31px)
 * 데스크톱: 높이 92 / 패딩 24·100 / 로고 사이 12 · 하마 44 · HAMA 28px 900 행간 140%(39px)
 * 로그인 버튼(이용약관/로그인): 104 × 40 / 패딩 8·16 / 반경 12 / 랜딩과 같은 그라데이션 / 16px 700 행간 150%(24px)
 * 로고는 랜딩으로, 로그인은 로그인 화면으로 이동합니다.
 * 이미 로그인한 상태면 같은 버튼이 "홈으로"가 되고 홈으로 갑니다 (랜딩의 "홈으로 가기"와 같은 방식).
 */
export default function TermsHeader() {
  const loggedIn = useAuthStore((s) => s.status === 'authenticated')

  // 약관은 로그인 없이도 열리는 화면이라, 바로 들어오면 refreshToken 쿠키로 로그인 상태를 한 번 확인합니다.
  useEffect(() => {
    if (useAuthStore.getState().status === 'unknown') void restoreSession()
  }, [])

  return (
    <header className="flex h-[72px] w-full items-center justify-between px-[20px] py-[16px] desktop:h-[92px] desktop:px-[100px] desktop:py-[24px]">
      <Link to={paths.landing} className="flex items-center gap-[8px] desktop:gap-[12px]">
        <img src={hippo} alt="" className="size-[36px] desktop:size-[44px]" />
        <span className="text-[22px] leading-[31px] font-black text-brand-main desktop:text-[28px] desktop:leading-[39px]">HAMA</span>
      </Link>
      <Link
        to={loggedIn ? paths.home : paths.login}
        className="flex h-[40px] w-[104px] items-center justify-center rounded-[12px] bg-[linear-gradient(90.6deg,#0950be_-18.89%,#042558_129.62%)] px-[16px] py-[8px] text-center text-[16px] leading-[24px] font-bold text-white-w1"
      >
        {loggedIn ? '홈으로' : '로그인'}
      </Link>
    </header>
  )
}
