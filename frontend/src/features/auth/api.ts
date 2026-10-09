import { api, refreshAccessToken } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { useAuthStore, type User } from './store'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */
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
  await loadMe()
}

/**
 * 로그인. 성공하면 accessToken 을 메모리에 저장합니다(refreshToken 은 HttpOnly 쿠키로 옴).
 * 실패: 401 INVALID_CREDENTIALS, 15분에 5회 초과 시 429 TOO_MANY_LOGIN_ATTEMPTS
 */
export async function login(body: LoginRequest) {
  const res = await api.post<ApiResponse<TokenResponse>>('/api/auth/login', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
  await loadMe()
}

/** 내 정보(GET /api/users/me)를 불러와 저장합니다. 실패해도 로그인 상태는 그대로 둡니다. */
export async function loadMe() {
  try {
    const res = await api.get<ApiResponse<User>>('/api/users/me')
    useAuthStore.getState().setUser(res.data.data)
  } catch {
    // 토큰이 만료돼 refresh 까지 실패하면 인터셉터가 로그아웃 상태로 바꿉니다.
  }
}

/** 로그아웃. 서버가 refreshToken 쿠키를 지우고, 메모리의 토큰도 비웁니다. */
export async function logout() {
  try {
    await api.post('/api/auth/logout')
  } finally {
    useAuthStore.getState().clear()
  }
}

/**
 * 새로고침 등으로 메모리 토큰이 사라졌을 때 refreshToken 쿠키로 로그인 상태를 되살립니다.
 * 쿠키가 없거나 만료됐으면 'unauthenticated' 가 됩니다.
 */
export async function restoreSession() {
  try {
    await refreshAccessToken()
  } catch {
    return
  }
  await loadMe()
}
