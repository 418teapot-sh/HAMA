import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../features/auth/store'
import type { ApiResponse } from './types'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/**
 * 모든 API 호출은 이 인스턴스를 씁니다.
 * - withCredentials: refreshToken 쿠키(HttpOnly)가 오가려면 꼭 켜야 합니다.
 * - 요청마다 메모리의 accessToken 을 Authorization 헤더에 붙입니다.
 * - 401 이 오면 /api/auth/refresh 로 재발급받고 원래 요청을 한 번 다시 보냅니다.
 */
export const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** refresh 는 인터셉터를 타지 않도록 별도 인스턴스로 부릅니다(무한 재시도 방지). */
const refreshClient = axios.create({ baseURL: API_URL, withCredentials: true })

/** 여러 요청이 동시에 401 을 받아도 refresh 는 한 번만 보냅니다(리프레시 토큰이 매번 교체되기 때문). */
let refreshPromise: Promise<string> | null = null

export function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiResponse<{ accessToken: string }>>('/api/auth/refresh')
      .then((res) => {
        const token = res.data.data.accessToken
        useAuthStore.getState().setAccessToken(token)
        return token
      })
      .catch((error: unknown) => {
        useAuthStore.getState().clear()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetryableConfig | undefined
    const isAuthEndpoint = original?.url?.startsWith('/api/auth/')

    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true
      try {
        const token = await refreshAccessToken()
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      } catch {
        // refresh 도 실패하면 store 가 'unauthenticated' 가 됩니다.
      }
    }
    return Promise.reject(error)
  },
)

/** 실패 응답에서 백엔드 에러 정보를 꺼냅니다. 에러 분기는 message 가 아니라 code 로 합니다(README). */
export function getApiError(error: unknown) {
  if (axios.isAxiosError<ApiResponse<null>>(error)) {
    return error.response?.data?.error ?? null
  }
  return null
}
