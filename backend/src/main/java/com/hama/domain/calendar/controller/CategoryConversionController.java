package com.hama.domain.calendar.controller;

import com.hama.domain.calendar.dto.CategoryConversionResponse;
import com.hama.domain.calendar.dto.ConvertCategoryRequest;
import com.hama.domain.calendar.service.CategoryConversionService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar/category-conversions")
@RequiredArgsConstructor
@Tag(name = "캘린더")
public class CategoryConversionController {
    private final CategoryConversionService service;

    @Operation(summary = "할일·기타·일정 카테고리 전환", description = "TASK↔OTHER는 같은 ID. TASK/OTHER↔PERSONAL은 원본을 소프트 삭제하고 새 ID를 반환합니다. "
            + "일정으로 전환할 때 startAt/endAt(KST)이 필수이며 내용은 기존 일정 규칙인 100자 이하여야 합니다. "
            + "일정에서 전환할 때 todoDate 필수, startTime/endTime은 함께 지정하거나 생략합니다. 제목·메모는 보존합니다. "
            + "완료된 투두는 완료 취소 후 가능. 목표 투두·고정·반복 일정은 전환 불가. "
            + "TASK/OTHER 원본의 실제 분류와 sourceType이 다르면 400 CALENDAR_CONVERSION_INVALID. 동일 요청을 자동 재전송하지 말고 응답 유실 시 캘린더를 재조회합니다. 성공은 200, 상태 제한409, 입력400, 타인403, 없는 원본404.")
    @PostMapping
    public ApiResponse<CategoryConversionResponse> convert(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConvertCategoryRequest request) {
        return ApiResponse.success(service.convert(user.userId(), request));
    }
}
