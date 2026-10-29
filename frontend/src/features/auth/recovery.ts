import { getApiError } from '../../api/client'

/** 서버가 문구를 주지 않았을 때(네트워크 오류 등) 보여줄 문구. 회원가입 화면과 같습니다. */
export const FALLBACK_ERROR = '잠시 후 다시 시도해 주세요'

/**
 * 계정 찾기 요청 실패를 화면에 나눠 보여 줍니다 (회원가입 화면과 같은 방식).
 * - VALIDATION_FAILED: 입력칸 아래 (fields 의 키 = 요청 필드 이름)
 * - 그 밖의 오류(429 TOO_MANY_REQUESTS, 만료된 링크 등): 토스트에 백엔드 문구
 * TODO: 실패 안내 디자인이 없어 회원가입과 같은 방식으로 둠. 확인 필요
 */
export function splitRecoveryError(error: unknown) {
  const apiError = getApiError(error)
  if (apiError?.code === 'VALIDATION_FAILED' && apiError.fields) {
    return { fields: apiError.fields, toast: undefined }
  }
  return { fields: {} as Record<string, string>, toast: apiError?.message ?? FALLBACK_ERROR }
}
