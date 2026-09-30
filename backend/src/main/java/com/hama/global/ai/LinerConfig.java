package com.hama.global.ai;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 라이너 전용 WebClient. 인증 헤더는 {@link LinerAiClient} 가 요청마다 붙입니다.
 *
 * <p>나중에 Paddle 등 다른 WebClient 빈이 생기면 타입만으로는 구분이 안 되므로
 * 주입받는 쪽은 {@code @Qualifier(LinerConfig.LINER_WEB_CLIENT)} 를 씁니다.
 */
@Configuration
public class LinerConfig {

    public static final String LINER_WEB_CLIENT = "linerWebClient";

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    @Bean(LINER_WEB_CLIENT)
    public WebClient linerWebClient(LinerProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
