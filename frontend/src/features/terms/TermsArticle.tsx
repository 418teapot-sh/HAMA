import { Fragment, type ReactNode } from 'react'
import type { PolicyArticle, PolicyBlock, PolicyListItem } from './policies'

/** 구분선 1px Black/Gray10 */
export function TermsDivider() {
  return <div className="h-px w-full shrink-0 bg-black-gray10" />
}

/** 조항 제목 · 모바일 20px 700 행간 140%(28px) / 데스크톱 28px 행간 140%(39px) / Black/Black */
export function TermsHeading({ children }: { children: ReactNode }) {
  return <h2 className="text-[20px] leading-[28px] font-bold text-black-black desktop:text-[28px] desktop:leading-[39px]">{children}</h2>
}

/** 조항 본문 글자 (Figma 이용약관/Mobile · Desktop/body): 모바일 14px 400 행간 150%(21px) / 데스크톱 20px 행간 150%(30px) / Black/Gray80 */
export const BODY_TEXT = 'text-[14px] leading-[21px] text-black-gray80 desktop:text-[20px] desktop:leading-[30px]'

const EMAIL = /([\w.+-]+@[\w-]+\.[\w.]+)/

/** 문구 안의 이메일 주소는 누르면 메일 앱이 열리게 링크로 바꿉니다. */
function PolicyText({ text }: { text: string }) {
  return text.split(EMAIL).map((part, i) =>
    i % 2 === 1 ? (
      <a key={i} href={`mailto:${part}`} className="underline underline-offset-2">
        {part}
      </a>
    ) : (
      <Fragment key={i}>{part}</Fragment>
    ),
  )
}

/** 목록 (Figma 에 없음): 글머리 • · 안쪽 목록 ◦ / 들여쓰기 1.25em / 항목 사이 4px(데스크톱 6px) */
function PolicyList({ items, nested }: { items: (PolicyListItem | string)[]; nested?: boolean }) {
  return (
    <ul className={`flex flex-col gap-[4px] pl-[1.25em] desktop:gap-[6px] ${nested ? 'mt-[4px] list-[circle] desktop:mt-[6px]' : 'list-disc'}`}>
      {items.map((item) => {
        const text = typeof item === 'string' ? item : item.text
        return (
          <li key={text}>
            <PolicyText text={text} />
            {typeof item !== 'string' && <PolicyList items={item.sub} nested />}
          </li>
        )
      })}
    </ul>
  )
}

/**
 * 표 (Figma 에 없음)
 * 테두리 1px Black/Gray10 / 머리 줄 배경 Card_Back_2 40% · 700 Brand/Main (목차 상자와 같은 색)
 * 글자 모바일 12px 행간 18px · 칸 패딩 8·10 / 데스크톱 18px 행간 27px · 칸 패딩 12·16
 */
function PolicyTable({ head, rows }: { head: string[]; rows: string[][] }) {
  const cell = 'border border-black-gray10 px-[10px] py-[8px] align-top break-keep desktop:px-[16px] desktop:py-[12px]'
  return (
    <table className="w-full border-collapse text-left text-[12px] leading-[18px] text-black-gray80 desktop:text-[18px] desktop:leading-[27px]">
      <thead>
        <tr className="bg-card-back-2/40 font-bold text-brand-main">
          {head.map((h) => (
            <th key={h} scope="col" className={cell}>
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row[0]}>
            {row.map((value, i) => (
              <td key={i} className={cell}>
                {value}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * Paddle 고지 상자 (Figma 제9조): 패딩 20(데스크톱 24) / 배경 White/W1 / 테두리 1px #5480AE / 반경 12
 * 글자 14px(20px) 500 행간 160% → 22px(32px) Brand/Main
 */
function PaddleNotice({ text }: { text: string }) {
  return (
    <p className="w-full rounded-[12px] border border-[#5480ae] bg-white-w1 p-[20px] text-[14px] leading-[22px] font-medium text-brand-main desktop:p-[24px] desktop:text-[20px] desktop:leading-[32px]">
      {text}
    </p>
  )
}

function Block({ block }: { block: PolicyBlock }) {
  if (typeof block === 'string') {
    return (
      <p>
        <PolicyText text={block} />
      </p>
    )
  }
  if ('list' in block) return <PolicyList items={block.list} />
  if ('table' in block) return <PolicyTable {...block.table} />
  return <PaddleNotice text={block.notice} />
}

/** 조항 본문: 문단 · 목록 · 표를 차례로 (사이 모바일 12 / 데스크톱 16) */
export function PolicyBlocks({ blocks }: { blocks: PolicyBlock[] }) {
  return (
    <div className={`flex w-full flex-col gap-[12px] desktop:gap-[16px] ${BODY_TEXT}`}>
      {blocks.map((block, i) => (
        <Block key={i} block={block} />
      ))}
    </div>
  )
}

interface TermsArticleProps {
  id: string
  article: PolicyArticle
  /** 아래 구분선 (마지막 조항에는 없음) */
  divider?: boolean
}

/**
 * 조항 한 칸 (Figma 제N조 묶음): [제목 + 본문(사이 12 · 데스크톱 16)] + 구분선, 사이 모바일 16 / 데스크톱 20
 */
export default function TermsArticle({ id, article, divider = true }: TermsArticleProps) {
  return (
    <section id={id} className="flex w-full scroll-mt-[20px] flex-col gap-[16px] desktop:gap-[20px]">
      <div className="flex w-full flex-col gap-[12px] desktop:gap-[16px]">
        <TermsHeading>{article.title}</TermsHeading>
        <PolicyBlocks blocks={article.blocks} />
      </div>
      {divider && <TermsDivider />}
    </section>
  )
}
