package com.hama.domain.goalai;

import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** 미리 넣어 둔 JSON 문자열을 순서대로 돌려주고, 받은 요청을 기록합니다. */
public class FakeAiClient implements AiClient {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<AiRequest> requests = new ArrayList<>();

    public synchronized void answer(String json) {
        answers.add(json);
    }

    public synchronized List<AiRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized void reset() {
        answers.clear();
        requests.clear();
    }

    @Override
    public synchronized String chat(AiRequest request) {
        requests.add(request);
        return next();
    }

    @Override
    public synchronized <T> T chatForJson(AiRequest request, Class<T> type) {
        requests.add(request);
        return mapper.readValue(next(), type);
    }

    private String next() {
        String answer = answers.poll();
        if (answer == null) {
            throw new IllegalStateException("FakeAiClient 에 준비된 응답이 없습니다.");
        }
        return answer;
    }
}
