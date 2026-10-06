package com.hama.global.response;

import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 목록 API 의 공통 페이징 형식입니다. {@code ApiResponse.success(PageResponse.from(...))} 로 감싸서 반환합니다.
 *
 * <p>Spring 의 {@code Page} 를 그대로 내보내면 pageable·sort 등 내부 필드가 전부 노출되고
 * 버전마다 형식이 바뀌어서, 프론트 타입이 흔들리지 않게 필요한 4개만 담습니다.
 */
@Schema(description = "페이징 응답")
public record PageResponse<T>(
        @Schema(description = "현재 페이지 항목") List<T> content,
        @Schema(description = "페이지 번호 (0부터)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 항목 수", example = "1") long totalElements
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * 쿼리 파라미터 page·size 를 검증해 정렬 없는 Pageable 로 바꿉니다. 정렬은 쿼리의 order by 가 정합니다.
     * page 는 0 이상, size 는 1~{@value #MAX_SIZE} 이고 벗어나면 400 입니다.
     */
    public static Pageable pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED,
                    "page 는 0 이상, size 는 1~%d 사이여야 합니다.".formatted(MAX_SIZE));
        }
        return PageRequest.of(page, size);
    }
}
