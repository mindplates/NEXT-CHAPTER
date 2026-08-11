package com.mindplates.nextchapter.application.generation.port.out;

/**
 * LLM 완성 요청.
 *
 * <p>{@code apiKey} 를 요청에 실어 보내는 이유는 클라이언트를 무상태로 두기 위해서다. 키를 빈 안에
 * 캐시하면 관리 화면에서 교체해도 재기동 전까지 옛 키를 계속 쓰고, 그건 "관리 화면에서 교체한다"는
 * 결정을 무력화한다.
 *
 * @param model 단계 설정에서 온 모델 식별자. 호출부는 이 값을 만들지 않는다
 */
public record LlmCompletionRequest(
        String model, String apiKey, String systemPrompt, String userPrompt, int maxTokens, Double temperature) {}
