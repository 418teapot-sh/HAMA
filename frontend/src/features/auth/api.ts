import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { useAuthStore } from './store'

export interface LoginRequest {
  email: string
  password: string
}

interface TokenResponse {
  accessToken: string
}

/**
 * 로그인. 성공하면 accessToken 을 메모리에 저장합니다(refreshToken 은 HttpOnly 쿠키로 옴).
 * 실패: 401 INVALID_CREDENTIALS, 15분에 5회 초과 시 429 TOO_MANY_LOGIN_ATTEMPTS
 */
export async function login(body: LoginRequest) {
  const res = await api.post<ApiResponse<TokenResponse>>('/api/auth/login', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
}
