import { useEffect } from 'react'
import { useDesktopZoom } from '../landing/useDesktopZoom'
import { articleId, type PolicyDoc } from './policies'
import TermsArticle, { PolicyBlocks, TermsDivider } from './TermsArticle'
import TermsFooter from './TermsFooter'
import TermsHeader from './TermsHeader'
import TermsToc from './TermsToc'

/**
 * 약관 · 정책 페이지 공통 틀 (Figma 온보딩 > 이용약관 · Mobile · 390 / 이용약관 · Desktop · 1512)
 * 이용약관 · 환불 및 청약철회 정책 · 개인정보처리방침이 같은 틀을 씁니다. (환불 · 개인정보는 Figma 에 따로 없음)
 * 랜딩과 같이 1024px(desktop) 부터 데스크톱 디자인, 1024 ~ 1511px 은 1512px 기준 비율 그대로 줄여 보여 줍니다.
 * 배경 White/W1 / 헤더 · 구분선 · 본문 · 구분선 · 푸터 세로
 * 본문 · 모바일: 패딩 40·20·48 / 사이 32 / 제목 묶음 아래로 목차 · 조항 세로(사이 36)
 * 본문 · 데스크톱: 너비 1312 · 패딩 72·100·80 / 사이 64 / 왼쪽 목차(240) · 오른쪽 조항(1008) 가로(사이 64)
 */
export default function PolicyPage({ doc }: { doc: PolicyDoc }) {
  const zoom = useDesktopZoom()

  // 다른 페이지 아래쪽(랜딩 푸터 등)에서 넘어와도 맨 위부터 보여 줍니다.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [doc.key])

  return (
    <main className="flex min-h-dvh w-full flex-col items-center bg-white-w1" style={{ zoom }}>
      <TermsHeader />
      <TermsDivider />

      <div className="flex w-full flex-col gap-[32px] px-[20px] pt-[40px] pb-[48px] desktop:gap-[64px] desktop:px-[100px] desktop:pt-[72px] desktop:pb-[80px]">
        {/*
          페이지 제목 · 사이 모바일 12 / 데스크톱 16
          약관 및 정책 14px(18px) 700 행간 150% #5480AE / 제목 32px(56px) 900 행간 140% / 날짜 12px(18px) 400 행간 150% Black/Gray60
        */}
        <div className="flex w-full flex-col gap-[12px] desktop:gap-[16px]">
          <p className="text-[14px] leading-[21px] font-bold text-[#5480ae] desktop:text-[18px] desktop:leading-[27px]">약관 및 정책</p>
          <h1 className="text-[32px] leading-[45px] font-black text-black-black desktop:text-[56px] desktop:leading-[78px]">{doc.title}</h1>
          <p className="text-[12px] leading-[18px] text-black-gray60 desktop:text-[18px] desktop:leading-[27px]">{doc.updatedAt}</p>
        </div>

        <div className="flex w-full flex-col gap-[36px] desktop:flex-row desktop:items-start desktop:gap-[64px]">
          <TermsToc doc={doc} />

          {/* 조항 · 사이 모바일 28 / 데스크톱 36 (데스크톱은 목차 옆 남은 너비 1008) */}
          <div className="flex w-full min-w-0 flex-col gap-[28px] desktop:w-[1008px] desktop:gap-[36px]">
            {doc.intro && (
              <div className="flex w-full flex-col gap-[16px] desktop:gap-[20px]">
                <PolicyBlocks blocks={[doc.intro]} />
                <TermsDivider />
              </div>
            )}
            {doc.articles.map((article, index) => (
              <TermsArticle key={article.title} id={articleId(index)} article={article} divider={index < doc.articles.length - 1} />
            ))}
          </div>
        </div>
      </div>

      <TermsDivider />
      <TermsFooter />
    </main>
  )
}
