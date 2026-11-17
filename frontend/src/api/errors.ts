import axios from 'axios'
import type { ApiResponse } from './types'

/** 서버가 문구를 주지 않았을 때(네트워크 오류 등) 보여 줄 문구 */
export const FALLBACK_ERROR = '잠시 후 다시 시도해 주세요'

/** 실패 응답에서 백엔드 에러 정보를 꺼냅니다. 에러 분기는 message 가 아니라 code 로 합니다(README). */
export function getApiError(error: unknown) {
  if (axios.isAxiosError<ApiResponse<null>>(error)) {
    return error.response?.data?.error ?? null
  }
  return null
}

/**
 * 실패를 토스트 · 안내 문구 하나로 바꿉니다. 고르는 순서:
 * 1. 400 VALIDATION_FAILED 면 서버가 칸별로 준 구체적인 문구 (예: "내용은 255자 이하입니다.")
 * 2. 화면에서 따로 정한 코드별 문구 (messages)
 * 3. 서버 문구
 * 4. FALLBACK_ERROR
 */
export function getErrorMessage(error: unknown, messages: Record<string, string> = {}) {
  const apiError = getApiError(error)
  if (!apiError) return FALLBACK_ERROR
  const fieldMessage = apiError.fields ? Object.values(apiError.fields).find(Boolean) : undefined
  return fieldMessage ?? messages[apiError.code] ?? apiError.message ?? FALLBACK_ERROR
}
