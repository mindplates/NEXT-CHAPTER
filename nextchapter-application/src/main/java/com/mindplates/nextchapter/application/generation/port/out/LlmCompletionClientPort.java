package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

/**
 * 벤더 하나에 대한 완성 호출. 벤더마다 구현이 하나씩 붙는다.
 *
 * <p>{@link #vendor()} 를 인터페이스에 둔 이유는 레지스트리가 구현 클래스 이름이나 빈 이름에 의존하지
 * 않고 스스로 벤더를 알 수 있게 하기 위해서다 — 벤더 추가가 "구현 하나 등록"으로 끝나야 한다.
 */
public interface LlmCompletionClientPort {

    AiVendor vendor();

    LlmCompletionResult complete(LlmCompletionRequest request);
}
