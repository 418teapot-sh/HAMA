import { paths } from '../../paths'
import PolicyLink from './PolicyLink'

/**
 * 정책 링크. 이용약관 → 이 페이지, 요금제 → 랜딩 요금제 섹션.
 * TODO: 환불 및 청약철회 · 개인정보처리방침 페이지가 생기면 연결
 */
const LINKS = [
  { label: '이용약관', to: paths.terms },
  { label: '환불 및 청약철회' },
  { label: '개인정보처리방침' },
  { label: '요금제', to: `${paths.landing}#pricing` },
]

/**
 * 이용약관 푸터 (Figma 푸터, 배경 없음 · 왼쪽 정렬)
 * 모바일: 패딩 28·20·32 / 사이 20 / 링크 11px 700 행간 150%(Figma 상자 17px) 사이 12 / 운영자 정보 10px 400 행간 160%(16px) 자간 -3%
 * 데스크톱: 패딩 40·100·48 / 사이 24 / 링크 18px 700 행간 150%(27px) 사이 32 / 운영자 정보 16px 400 행간 160%(26px)
 * 링크 #5480AE · 운영자 정보 Black/Gray60
 */
export default function TermsFooter() {
  return (
    <footer className="flex w-full flex-col gap-[20px] px-[20px] pt-[28px] pb-[32px] desktop:gap-[24px] desktop:px-[100px] desktop:pt-[40px] desktop:pb-[48px]">
      <nav className="flex flex-wrap gap-[12px] desktop:gap-[32px]">
        {LINKS.map((link) => (
          <PolicyLink
            key={link.label}
            to={link.to}
            className="text-[11px] leading-[17px] font-bold text-[#5480ae] desktop:text-[18px] desktop:leading-[27px]"
          >
            {link.label}
          </PolicyLink>
        ))}
      </nav>
      <p className="text-[10px] leading-[16px] tracking-[-0.03em] text-black-gray60 desktop:text-[16px] desktop:leading-[26px] desktop:tracking-normal">
        운영자 [법적 이름] · [주소] · [support@도메인] · 호스팅 [예: Amazon Web Services, Inc.]
      </p>
    </footer>
  )
}
