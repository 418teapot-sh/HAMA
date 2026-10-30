package com.hama.domain.goalai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "movedCount 는 날짜나 시간이 바뀐 투두 수, unplacedCount 는 배치 실패로 원본을 보존한 투두 수")
public record ReplanResponse(int movedCount, int unplacedCount) {
}
