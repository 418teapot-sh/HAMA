import type { CSSProperties, ReactNode } from 'react'

/** 구분선 1px Black/Gray10 */
export function TermsDivider() {
  return <div className="h-px w-full shrink-0 bg-black-gray10" />
}

/** 조항 제목 · 모바일 20px 700 행간 140%(28px) / 데스크톱 28px 행간 140%(39px) / Black/Black */
export function TermsHeading({ children }: { children: ReactNode }) {
  return <h2 className="text-[20px] leading-[28px] font-bold text-black-black desktop:text-[28px] desktop:leading-[39px]">{children}</h2>
}

interface TermsBodyProps {
  children: ReactNode
  /** 자간 · 모바일 (Figma 조항마다 다름) */
  tracking?: string
  /** 자간 · 데스크톱 */
  desktopTracking?: string
}

/**
 * 조항 본문 · 모바일 14px 400 행간 150%(21px) / 데스크톱 20px 행간 150%(30px) / Black/Gray80
 * 문구 안의 줄바꿈(\n)은 그대로 보여 줍니다. (제8조 문단 나눔)
 */
export function TermsBody({ children, tracking = '0em', desktopTracking = '0em' }: TermsBodyProps) {
  const style = { '--tracking': tracking, '--desktop-tracking': desktopTracking } as CSSProperties
  return (
    <p
      style={style}
      className="text-[14px] leading-[21px] tracking-(--tracking) whitespace-pre-line text-black-gray80 desktop:text-[20px] desktop:leading-[30px] desktop:tracking-(--desktop-tracking)"
    >
      {children}
    </p>
  )
}

interface TermsArticleProps {
  id: string
  children: ReactNode
  /** 아래 구분선 (마지막 제10조에는 없음) */
  divider?: boolean
}

/**
 * 조항 한 칸 (Figma 제N조 묶음): 내용 + 구분선, 사이 모바일 16 / 데스크톱 20
 * 내용은 보통 TermsArticleText(제목 + 본문), 제9조만 제목 · Paddle 고지 · 본문을 바로 넣습니다.
 */
export default function TermsArticle({ id, children, divider = true }: TermsArticleProps) {
  return (
    <section id={id} className="flex w-full scroll-mt-[20px] flex-col gap-[16px] desktop:gap-[20px]">
      {children}
      {divider && <TermsDivider />}
    </section>
  )
}

/** 조항 제목 + 본문 (Figma 이용약관/조항/Mobile · Desktop 컴포넌트): 사이 모바일 12 / 데스크톱 16 */
export function TermsArticleText({ title, body, ...tracking }: Omit<TermsBodyProps, 'children'> & { title: string; body: string }) {
  return (
    <div className="flex w-full flex-col gap-[12px] desktop:gap-[16px]">
      <TermsHeading>{title}</TermsHeading>
      <TermsBody {...tracking}>{body}</TermsBody>
    </div>
  )
}
