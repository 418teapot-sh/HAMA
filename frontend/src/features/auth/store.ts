import { create } from 'zustand'

/**
 * 로그인 상태 저장소.
 *
 * accessToken 은 메모리(Zustand)에만 둡니다. README 규칙대로 localStorage 에는 넣지 않습니다.
 * 새로고침하면 사라지지만, 앱 시작 시 /api/v1/auth/refresh 를 한 번 부르면 HttpOnly 쿠키로 복구됩니다.
 */
export type AuthStatus = 'unknown' | 'authenticated' | 'unauthenticated'

/** GET /api/v1/users/me 응답 (Swagger UserResponse) */
export interface User {
  id: number
  email: string
  name: string
  isPremium: boolean
}

interface AuthState {
  accessToken: string | null
  status: AuthStatus
  /** 로그인한 사용자 정보. 토큰은 있는데 아직 못 불러왔으면 null */
  user: User | null
  setAccessToken: (token: string) => void
  setUser: (user: User) => void
  clear: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  status: 'unknown',
  user: null,
  setAccessToken: (token) => set({ accessToken: token, status: 'authenticated' }),
  setUser: (user) => set({ user }),
  clear: () => set({ accessToken: null, status: 'unauthenticated', user: null }),
}))
