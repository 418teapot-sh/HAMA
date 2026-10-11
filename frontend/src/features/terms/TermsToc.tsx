import PolicyLink from './PolicyLink'
import { TermsDivider } from './TermsArticle'
import { articleId } from './termsContent'

/** 목차 · 다른 문서 링크 글자: 500 / #5480AE (Figma 에만 있는 색이라 값으로 씀) */
const ITEM = 'text-left font-medium text-[#5480ae]'

interface TermsTocProps {
  /** 목차 이름 (조항 순서대로) */
  labels: string[]
}

/**
 * 목차 상자 (Figma 목차)
 * 공통: 배경 Card_Back_2 40% / 반경 12
 * 모바일: 패딩 20 / 사이 16 / 목차 두 칸(1~5, 6~10 · 칸 사이 16 · 항목 사이 12) / 항목 12px 행간 150%(18px)
 * 데스크톱: 너비 240 / 패딩 24 / 모든 줄 사이 20 (한 칸으로 1~10) / 항목 16px 행간 150%(24px)
 *   데스크톱은 칸 묶음을 display: contents 로 풀어 항목이 상자 바로 아래에 오게 합니다.
 *   (모바일 li 는 flex 로 두어 행간이 li 기본 글자 크기 16px 기준으로 늘어나지 않게 합니다)
 * 목차 항목을 누르면 그 조항으로 스크롤합니다.
 */
export default function TermsToc({ labels }: TermsTocProps) {
  const half = Math.ceil(labels.length / 2)
  const columns = [labels.slice(0, half), labels.slice(half)]

  const scrollTo = (index: number) => {
    document.getElementById(articleId(index))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <nav
      aria-label="목차"
      className="flex w-full shrink-0 flex-col gap-[16px] rounded-[12px] bg-card-back-2/40 p-[20px] desktop:w-[240px] desktop:gap-[20px] desktop:p-[24px]"
    >
      <h2 className="text-[16px] leading-[24px] font-bold text-brand-main desktop:text-[20px] desktop:leading-[30px]">목차</h2>
      <div className="flex gap-[16px] desktop:contents">
        {columns.map((column, c) => (
          <ol key={c} className="flex flex-1 flex-col gap-[12px] desktop:contents">
            {column.map((label, i) => {
              const index = c * half + i
              return (
                <li key={label} className="flex desktop:contents">
                  <button
                    type="button"
                    onClick={() => scrollTo(index)}
                    className={`${ITEM} text-[12px] leading-[18px] desktop:text-[16px] desktop:leading-[24px]`}
                  >
                    {index + 1}. {label}
                  </button>
                </li>
              )
            })}
          </ol>
        ))}
      </div>
      <TermsDivider />
      <h2 className="text-[14px] leading-[21px] font-bold text-brand-main desktop:text-[18px] desktop:leading-[27px]">다른 문서</h2>
      {/* TODO: 환불 및 청약철회 · 개인정보처리방침 페이지가 생기면 연결 */}
      <PolicyLink className={`${ITEM} text-[14px] leading-[21px] desktop:text-[16px] desktop:leading-[24px]`}>환불 및 청약철회</PolicyLink>
      <PolicyLink className={`${ITEM} text-[14px] leading-[21px] desktop:text-[16px] desktop:leading-[24px]`}>개인정보처리방침</PolicyLink>
    </nav>
  )
}
