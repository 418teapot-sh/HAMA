package com.hama.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void offset_이_int_범위_안이면_통과한다() {
        assertThat(PageResponse.pageRequest(Integer.MAX_VALUE, 1).getOffset()).isEqualTo(Integer.MAX_VALUE);
        assertThat(PageResponse.pageRequest(Integer.MAX_VALUE / 100, 100).getPageNumber())
                .isEqualTo(Integer.MAX_VALUE / 100);
    }

    @Test
    void offset_이_int_범위를_넘으면_VALIDATION_FAILED() {
        for (int[] params : new int[][]{{Integer.MAX_VALUE, 2}, {Integer.MAX_VALUE, 20}, {Integer.MAX_VALUE / 100 + 1, 100}}) {
            assertThatThrownBy(() -> PageResponse.pageRequest(params[0], params[1]))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
        }
    }
}
