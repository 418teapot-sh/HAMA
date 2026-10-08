package com.hama.domain.goalai.dto;

import java.util.List;
import java.util.Map;

/** 현실성 체크 결과. 세션에 저장해 두고 세션 조회(realityResult)에서도 그대로 보여줍니다. */
public record RealityResult(
        String verdict,
        String comment,
        Comparison comparison,
        boolean needsSplit,
        List<Suggestion> suggestions
) {

    /** availableHours·gapHours 는 서버가 초안의 기간과 주간 가용시간으로 계산합니다. 가용시간이 없으면 null 입니다. */
    public record Comparison(Integer requiredHours, Integer availableHours, Integer gapHours) {
    }

    /** patch 는 PATCH draft 본문으로 그대로 보낼 수 있는 값입니다. */
    public record Suggestion(String type, String label, Map<String, Object> patch) {
    }
}
