package com.hama.domain.shared.time;

import java.time.ZoneId;

/** BE3 날짜·시간 계약에 사용하는 한국 시간대입니다. */
public final class Be3Time {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Be3Time() {
    }
}
