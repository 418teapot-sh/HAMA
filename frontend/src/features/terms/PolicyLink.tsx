import type { ReactNode } from 'react'
import { Link } from 'react-router'

interface PolicyLinkProps {
  children: ReactNode
  /** 이동할 주소. 아직 페이지가 없는 문서(환불 및 청약철회 · 개인정보처리방침)는 비워 둡니다. */
  to?: string
  className?: string
}

/**
 * 약관 · 정책 문서 링크 (이용약관 헤더 · 목차의 다른 문서 · 푸터에서 같이 씀)
 * 이동할 곳이 있으면 링크, 아직 없으면 모양만 같은 버튼으로 둡니다.
 * TODO: 환불 및 청약철회 · 개인정보처리방침 페이지가 생기면 to 를 채웁니다.
 */
export default function PolicyLink({ children, to, className = '' }: PolicyLinkProps) {
  if (to) {
    return (
      <Link to={to} className={className}>
        {children}
      </Link>
    )
  }
  return (
    <button type="button" className={`text-left ${className}`}>
      {children}
    </button>
  )
}
