import { Link } from 'react-router'
import hippo from '../../assets/landing/hippo.webp'
import { paths } from '../../paths'

/**
 * 이용약관 헤더 (Figma 헤더)
 * 모바일: 높이 72 / 패딩 16·20 / 로고 사이 8 · 하마 36 · HAMA 22px 900 행간 140%(31px)
 * 데스크톱: 높이 92 / 패딩 24·100 / 로고 사이 12 · 하마 44 · HAMA 28px 900 행간 140%(39px)
 * 로그인 버튼(이용약관/로그인): 104 × 40 / 패딩 8·16 / 반경 12 / 랜딩과 같은 그라데이션 / 16px 700 행간 150%(24px)
 * 로고는 랜딩으로, 로그인은 로그인 화면으로 이동합니다.
 */
export default function TermsHeader() {
  return (
    <header className="flex h-[72px] w-full items-center justify-between px-[20px] py-[16px] desktop:h-[92px] desktop:px-[100px] desktop:py-[24px]">
      <Link to={paths.landing} className="flex items-center gap-[8px] desktop:gap-[12px]">
        <img src={hippo} alt="" className="size-[36px] desktop:size-[44px]" />
        <span className="text-[22px] leading-[31px] font-black text-brand-main desktop:text-[28px] desktop:leading-[39px]">HAMA</span>
      </Link>
      <Link
        to={paths.login}
        className="flex h-[40px] w-[104px] items-center justify-center rounded-[12px] bg-[linear-gradient(90.6deg,#0950be_-18.89%,#042558_129.62%)] px-[16px] py-[8px] text-center text-[16px] leading-[24px] font-bold text-white-w1"
      >
        로그인
      </Link>
    </header>
  )
}
