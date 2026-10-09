/** Figma 로그인 오류 화면의 이메일 오류 문구 그대로 */
export const EMAIL_FORMAT_ERROR = '올바른 이메일 형식이 아니에요.'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(email: string) {
  return EMAIL_PATTERN.test(email.trim())
}
