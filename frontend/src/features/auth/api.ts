import { api, refreshAccessToken } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { useAuthStore, type User } from './store'

/** 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다. */
export interface SignupRequest {
  email: string
  /** 영문·숫자·특수문자를 각각 1개 이상 포함한 8~20자 (공백·한글 불가) */
  password: string
  /** 50자 이하 */
  name: string
  /** 서비스 이용약관 동의 (필수, true 여야 가입) */
  termsAgreed: boolean
  /** 개인정보 수집 및 이용 동의 (필수, true 여야 가입) */
  privacyAgreed: boolean
  /** 만 14세 이상 확인 (필수, true 여야 가입) */
  ageConfirmed: boolean
  /** 마케팅 알림 수신 동의 (선택) */
  marketingAgreed: boolean
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
  const res = await api.post<ApiResponse<TokenResponse>>('/api/v1/auth/signup', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
  await loadMe()
}

/**
 * 로그인. 성공하면 accessToken 을 메모리에 저장합니다(refreshToken 은 HttpOnly 쿠키로 옴).
 * 실패: 401 INVALID_CREDENTIALS, 15분에 5회 초과 시 429 TOO_MANY_PASSWORD_ATTEMPTS
 */
export async function login(body: LoginRequest) {
  const res = await api.post<ApiResponse<TokenResponse>>('/api/v1/auth/login', body)
  useAuthStore.getState().setAccessToken(res.data.data.accessToken)
  await loadMe()
}

/** 내 정보(GET /api/v1/users/me)를 불러와 저장합니다. 실패해도 로그인 상태는 그대로 둡니다. */
export async function loadMe() {
  try {
    const res = await api.get<ApiResponse<User>>('/api/v1/users/me')
    useAuthStore.getState().setUser(res.data.data)
  } catch {
    // 토큰이 만료돼 refresh 까지 실패하면 인터셉터가 로그아웃 상태로 바꿉니다.
  }
}

/** 로그아웃. 서버가 refreshToken 쿠키를 지우고, 메모리의 토큰도 비웁니다. */
export async function logout() {
  try {
    await api.post('/api/v1/auth/logout')
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

/**
 * 회원탈퇴. 비밀번호를 확인한 뒤 계정과 모든 데이터를 지웁니다 (되돌릴 수 없음).
 * 성공하면 refreshToken 쿠키가 지워지므로 메모리의 토큰도 비웁니다.
 * 실패: 400 PASSWORD_MISMATCH, 15분에 5회 초과 시 429 TOO_MANY_PASSWORD_ATTEMPTS
 */
export async function withdraw(password: string) {
  await api.post('/api/v1/users/me/withdraw', { password })
  useAuthStore.getState().clear()
}

/**
 * 비밀번호 재설정 메일 요청. 가입 여부를 숨기려고 없는 이메일이어도 성공(200)합니다.
 * 메일의 링크는 {프론트}/reset-password?token=... (30분, 한 번만 사용). 다시 요청하면 옛 링크는 무효가 됩니다.
 * 실패: 400 VALIDATION_FAILED(fields.email), 이메일·IP 마다 15분에 5번 초과 시 429 TOO_MANY_REQUESTS
 */
export async function requestPasswordReset(email: string) {
  await api.post('/api/v1/auth/password-reset', { email })
}

/**
 * 새 비밀번호 설정. 성공하면 모든 기기의 refreshToken 이 지워져 새 비밀번호로 다시 로그인해야 합니다.
 * 실패: 400 INVALID_PASSWORD_RESET_TOKEN(만료 · 이미 사용 · 새 메일로 바뀜), 400 VALIDATION_FAILED(fields.newPassword)
 */
export async function confirmPasswordReset(token: string, newPassword: string) {
  await api.post('/api/v1/auth/password-reset/confirm', { token, newPassword })
}

/**
 * 이메일 찾기 문의. 운영자가 확인해 답변 이메일로 안내합니다(자동으로 찾아주지 않음).
 * 실패: 400 VALIDATION_FAILED(fields.name · fields.email), IP 마다 15분에 5번 초과 시 429 TOO_MANY_REQUESTS
 */
export async function submitEmailInquiry(name: string, email: string) {
  await api.post('/api/v1/auth/email-inquiry', { name, email })
}
