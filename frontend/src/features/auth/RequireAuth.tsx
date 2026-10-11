import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { paths } from '../../paths'
import { restoreSession } from './api'
import { useAuthStore } from './store'

/**
 * 로그인해야 들어갈 수 있는 화면을 감쌉니다.
 * - 아직 모르면(앱을 막 켰거나 새로고침) refreshToken 쿠키로 로그인 상태를 확인하고, 그동안은 아무것도 그리지 않습니다.
 * - 로그인 안 했으면 로그인 화면으로 보내고, 로그인 후 원래 가려던 화면으로 돌아오도록 위치를 넘깁니다.
 *   단, 첫 화면(/)으로 들어왔다면 로그인 화면 대신 랜딩으로 보냅니다. (랜딩 → 회원가입하고 시작하기 → 가입)
 */
export default function RequireAuth() {
  const status = useAuthStore((s) => s.status)
  const location = useLocation()

  useEffect(() => {
    if (status === 'unknown') void restoreSession()
  }, [status])

  if (status === 'unknown') return null
  if (status === 'unauthenticated') {
    // 첫 화면(/)으로 들어온 비로그인 사용자는 서비스 소개(랜딩)부터 보여 줍니다.
    if (location.pathname === paths.home) return <Navigate to={paths.landing} replace />
    return <Navigate to={paths.login} replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
