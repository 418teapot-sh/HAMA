import type { ReactNode } from 'react'
import { Link } from 'react-router'

interface PolicyLinkProps {
  children: ReactNode
  /** 앱 안 주소(/terms 등) 또는 mailto: 같은 바깥 주소 */
  to: string
  className?: string
}

/** 약관 · 정책 문서 링크 (목차의 다른 문서 · 약관 푸터 · 랜딩 푸터에서 같이 씀). mailto: 는 일반 링크로 엽니다. */
export default function PolicyLink({ children, to, className = '' }: PolicyLinkProps) {
  if (to.startsWith('mailto:')) {
    return (
      <a href={to} className={className}>
        {children}
      </a>
    )
  }
  return (
    <Link to={to} className={className}>
      {children}
    </Link>
  )
}
