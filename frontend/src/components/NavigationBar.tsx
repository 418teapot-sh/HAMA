import type { ReactNode } from 'react'

interface NavigationBarProps {
  /** 왼쪽 아이콘 (24×24) */
  leftIcon?: ReactNode
  leftLabel?: string
  onLeftClick?: () => void
}

/**
 * 상단 내비게이션 바 (Figma 컴포넌트: NavigationBar)
 * 높이 60(hug) / 패딩 12·20 / 아래 테두리 1px Black/Gray10 / 양쪽 정렬 space-between
 * [왼쪽 버튼 36×36 (아이콘 24×24)] [가운데 영역 채우기] [오른쪽 영역 32×32]
 *
 * 가운데 제목은 Figma 컴포넌트 속성(Show Title)으로 존재하지만,
 * 로그인 화면에서는 비어 있어 글자 스타일을 확인하지 못했습니다. 제목이 필요한 화면을 만들 때 추가합니다.
 */
export default function NavigationBar({ leftIcon, leftLabel, onLeftClick }: NavigationBarProps) {
  return (
    <header className="flex h-[60px] w-full items-center justify-between border-b border-black-gray10 px-[20px] py-[12px]">
      <div className="size-[36px]">
        {leftIcon && (
          <button
            type="button"
            aria-label={leftLabel}
            onClick={onLeftClick}
            className="flex size-[36px] items-center justify-center"
          >
            {leftIcon}
          </button>
        )}
      </div>
      <div className="flex-1" />
      <div className="size-[32px]" />
    </header>
  )
}
