import { create } from 'zustand'

/**
 * 로그인 상태 저장소.
 *
 * accessToken 은 메모리(Zustand)에만 둡니다. README 규칙대로 localStorage 에는 넣지 않습니다.
 * 새로고침하면 사라지지만, 앱 시작 시 /api/auth/refresh 를 한 번 부르면 HttpOnly 쿠키로 복구됩니다.
 */
export type AuthStatus = 'unknown' | 'authenticated' | 'unauthenticated'

interface AuthState {
  accessToken: string | null
  status: AuthStatus
  setAccessToken: (token: string) => void
  clear: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  status: 'unknown',
  setAccessToken: (token) => set({ accessToken: token, status: 'authenticated' }),
  clear: () => set({ accessToken: null, status: 'unauthenticated' }),
}))
