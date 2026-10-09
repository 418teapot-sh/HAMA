import type { ReactNode } from 'react'

interface NavigationBarProps {
  /**
   * Figma NavigationBar 변형
   * - 'default' (회원가입): [왼쪽 32×32, 아이콘 24 · 위/왼쪽 4] [제목 채우기] [오른쪽 36×36]
   * - 'close'   (로그인):   [왼쪽 36×36, 아이콘 24 · 위/왼쪽 6] [제목 채우기] [오른쪽 32×32]
   */
  variant?: 'default' | 'close'
  title?: string
  /** 왼쪽 아이콘 (24×24) */
  leftIcon?: ReactNode
  leftLabel?: string
  onLeftClick?: () => void
}

/**
 * 상단 내비게이션 바 (Figma 컴포넌트: NavigationBar)
 * 높이 60(hug) / 패딩 12·20 / 아래 테두리 1px Black/Gray10 / 양쪽 정렬 space-between
 * 제목: Heading/H6 (18/400/100%) Black/Black, 가운데 영역 안에서 가운데 정렬
 */
export default function NavigationBar({ variant = 'default', title, leftIcon, leftLabel, onLeftClick }: NavigationBarProps) {
  const leftSize = variant === 'close' ? 'size-[36px]' : 'size-[32px]'
  const rightSize = variant === 'close' ? 'size-[32px]' : 'size-[36px]'

  return (
    <header className="flex h-[60px] w-full items-center justify-between border-b border-black-gray10 px-[20px] py-[12px]">
      <div className={`shrink-0 ${leftSize}`}>
        {leftIcon && (
          <button
            type="button"
            aria-label={leftLabel}
            onClick={onLeftClick}
            className={`flex items-center justify-center ${leftSize}`}
          >
            {leftIcon}
          </button>
        )}
      </div>
      <div className="flex flex-1 items-center justify-center">
        {title && <h1 className="text-heading-h6 text-black-black">{title}</h1>}
      </div>
      <div className={`shrink-0 ${rightSize}`} />
    </header>
  )
}
