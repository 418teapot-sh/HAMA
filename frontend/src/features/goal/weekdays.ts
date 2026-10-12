/** 고정 일정 요일 (월요일부터) */
export const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'] as const
export type Weekday = (typeof WEEKDAYS)[number]
