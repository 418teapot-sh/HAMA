import { useEffect } from 'react'
import { useDesktopZoom } from '../features/landing/useDesktopZoom'
import TermsArticle, { TermsArticleText, TermsBody, TermsDivider, TermsHeading } from '../features/terms/TermsArticle'
import TermsFooter from '../features/terms/TermsFooter'
import TermsHeader from '../features/terms/TermsHeader'
import TermsToc from '../features/terms/TermsToc'
import { PADDLE_NOTICE, TERMS_ARTICLES, TERMS_UPDATED_AT, articleId } from '../features/terms/termsContent'

/** 결제 처리 주체(제9조) 순서 · Paddle 고지가 들어가는 조항 */
const PADDLE_ARTICLE_INDEX = 8

/**
 * 이용약관 (Figma 온보딩 > 이용약관 · Mobile · 390 / 이용약관 · Desktop · 1512)
 * 랜딩과 같이 1024px(desktop) 부터 데스크톱 디자인, 1024 ~ 1511px 은 1512px 기준 비율 그대로 줄여 보여 줍니다.
 * 배경 White/W1 / 헤더 · 구분선 · 본문 · 구분선 · 푸터 세로
 * 본문 · 모바일: 패딩 40·20·48 / 사이 32 / 제목 묶음 아래로 목차 · 조항 세로(사이 36)
 * 본문 · 데스크톱: 너비 1312 · 패딩 72·100·80 / 사이 64 / 왼쪽 목차(240) · 오른쪽 조항(1008) 가로(사이 64)
 */
export default function TermsPage() {
  const zoom = useDesktopZoom()

  // 랜딩 푸터 등 다른 페이지 아래쪽에서 넘어와도 맨 위부터 보여 줍니다.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [])

  return (
    <main className="flex min-h-dvh w-full flex-col items-center bg-white-w1" style={{ zoom }}>
      <TermsHeader />
      <TermsDivider />

      <div className="flex w-full flex-col gap-[32px] px-[20px] pt-[40px] pb-[48px] desktop:gap-[64px] desktop:px-[100px] desktop:pt-[72px] desktop:pb-[80px]">
        {/*
          페이지 제목 · 사이 모바일 12 / 데스크톱 16
          약관 및 정책 14px(18px) 700 행간 150% #5480AE / 이용약관 32px(56px) 900 행간 140% / 날짜 12px(18px) 400 행간 150% Black/Gray60
        */}
        <div className="flex w-full flex-col gap-[12px] desktop:gap-[16px]">
          <p className="text-[14px] leading-[21px] font-bold text-[#5480ae] desktop:text-[18px] desktop:leading-[27px]">약관 및 정책</p>
          <h1 className="text-[32px] leading-[45px] font-black text-black-black desktop:text-[56px] desktop:leading-[78px]">이용약관</h1>
          <p className="text-[12px] leading-[18px] text-black-gray60 desktop:text-[18px] desktop:leading-[27px]">{TERMS_UPDATED_AT}</p>
        </div>

        <div className="flex w-full flex-col gap-[36px] desktop:flex-row desktop:items-start desktop:gap-[64px]">
          <TermsToc labels={TERMS_ARTICLES.map((article) => article.tocLabel)} />

          {/* 조항 1~10 · 사이 모바일 28 / 데스크톱 36 */}
          <div className="flex w-full flex-col gap-[28px] desktop:w-[1008px] desktop:gap-[36px]">
            {TERMS_ARTICLES.map((article, index) => {
              const last = index === TERMS_ARTICLES.length - 1
              if (index === PADDLE_ARTICLE_INDEX) {
                return (
                  <TermsArticle key={article.title} id={articleId(index)}>
                    <TermsHeading>{article.title}</TermsHeading>
                    {/*
                      Paddle 고지 상자: 패딩 20(데스크톱 24) / 배경 White/W1 / 테두리 1px #5480AE / 반경 12
                      글자 14px(20px) 500 행간 160% → 22px(32px) Brand/Main
                    */}
                    <p className="w-full rounded-[12px] border border-[#5480ae] bg-white-w1 p-[20px] text-[14px] leading-[22px] font-medium text-brand-main desktop:p-[24px] desktop:text-[20px] desktop:leading-[32px]">
                      {PADDLE_NOTICE}
                    </p>
                    <TermsBody tracking={article.tracking} desktopTracking={article.desktopTracking}>
                      {article.body}
                    </TermsBody>
                  </TermsArticle>
                )
              }
              return (
                <TermsArticle key={article.title} id={articleId(index)} divider={!last}>
                  <TermsArticleText
                    title={article.title}
                    body={article.body}
                    tracking={article.tracking}
                    desktopTracking={article.desktopTracking}
                  />
                </TermsArticle>
              )
            })}
          </div>
        </div>
      </div>

      <TermsDivider />
      <TermsFooter />
    </main>
  )
}
