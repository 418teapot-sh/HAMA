/**
 * 목표 제목 · 고정 일정 내용 검사. 백엔드 GoalContent.MEANINGFUL_TEXT 와 같은 규칙으로,
 * 한글 음절이나 영문자가 2자 이상 있어야 합니다 ("4", "기", "ㄱㄱ" 는 거절).
 */
const MEANINGFUL_TEXT = /(?:[\s\S]*?[가-힣A-Za-z]){2}/

export function isMeaningfulText(text: string) {
  return MEANINGFUL_TEXT.test(text)
}

export const GOAL_TEXT_ERROR = '목표를 글자 2자 이상으로 구체적으로 적어주세요.'
export const SCHEDULE_TEXT_ERROR = '일정을 글자 2자 이상으로 구체적으로 적어주세요.'
