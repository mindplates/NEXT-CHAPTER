package com.mindplates.nextchapter.adapter.out.ai;

import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 벤더 클라이언트가 공유하는 {@link RestClient} 생성 규칙.
 *
 * <p>Boot 가 주는 {@code RestClient.Builder} 를 주입받지 않고 직접 만든다. 그 빈은 웹 자동설정이
 * 올라와야 존재하는데, 이 어댑터는 배치·컨슈머 프로세스처럼 웹 없이 뜨는 조립에서도 쓰여야 한다.
 *
 * <p>읽기 타임아웃이 분 단위인 것은 본문 생성이 실제로 그만큼 걸리기 때문이다. 짧게 잡으면 거의
 * 끝난 호출을 버리고 재시도해 토큰 비용만 두 배가 된다.
 */
final class AiRestClients {

    private AiRestClients() {}

    static RestClient create(String baseUrl, AiProviderProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
