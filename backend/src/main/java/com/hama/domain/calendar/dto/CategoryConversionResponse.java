package com.hama.domain.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전환 결과. 같은 todo 안의 TASK↔OTHER는 ID 유지, 일정↔투두는 새 ID. type+refId로 식별합니다.")
public record CategoryConversionResponse(CalendarType sourceType, Long sourceRefId, CalendarType type, Long refId) {
}
