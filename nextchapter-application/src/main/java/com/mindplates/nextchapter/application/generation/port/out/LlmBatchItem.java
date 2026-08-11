package com.mindplates.nextchapter.application.generation.port.out;

/**
 * 배치 안의 요청 하나.
 *
 * @param customId 결과를 요청에 되짚는 키. 벤더가 결과 순서를 보장하지 않으므로 <b>이것 없이는 어느 응답이
 *     어느 챕터의 것인지 알 수 없다.</b> 챕터 ID 를 쓴다
 */
public record LlmBatchItem(String customId, String systemPrompt, String userPrompt, int maxTokens) {}
