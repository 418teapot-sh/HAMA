/**
 * 백엔드 공통 응답 형식 (README "응답 형식" 참고)
 * 성공: { success: true, data, error: null, traceId }
 * 실패: { success: false, data: null, error: { code, message, fields }, traceId }
 */
export interface ApiError {
  code: string
  message: string
  /** VALIDATION_FAILED 일 때만 { 필드명: 메시지 } 가 오고, 그 외에는 null */
  fields: Record<string, string> | null
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: ApiError | null
  traceId: string
}
