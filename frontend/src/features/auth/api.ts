import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { useAuthStore } from './store'

export interface SignupRequest {
  email: string
  /** 8~64자 */
  password: string
  /** 50자 이하 */
  name: string
}

export interface LoginRequest {
  email: string
  password: string
}

interface TokenResponse {
  accessToken: string
}

/**
 * 회원가입. 가입과 동시에 로그인 처리되어 accessToken 이 옵니다(refreshToken 은 쿠키).
 * 실패: 409 EMAIL_ALREADY_EXISTS, 400 VALIDATION_FAILED
 */
export async function signup(body: SignupRequest) {
  const res = await api.post<ApiResponse<TokenResponse>>('/api/auth/signup', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
}

/**
 * 로그인. 성공하면 accessToken 을 메모리에 저장합니다(refreshToken 은 HttpOnly 쿠키로 옴).
 * 실패: 401 INVALID_CREDENTIALS, 15분에 5회 초과 시 429 TOO_MANY_LOGIN_ATTEMPTS
 */
export async function login(body: LoginRequest) {
  const res = await api.post<ApiResponse<TokenResponse>>('/api/auth/login', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
}
