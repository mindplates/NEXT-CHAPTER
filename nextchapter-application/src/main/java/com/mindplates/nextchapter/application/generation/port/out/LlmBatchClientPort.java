package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

/**
 * 벤더의 배치 API. 비동기 일괄 생성이므로 <b>전 토큰 50% 할인</b> 조건에 부합한다.
 *
 * <p>완성 클라이언트와 <b>다른 포트</b>로 둔다. 모든 벤더가 배치를 제공하지 않으므로 같은 인터페이스에
 * 넣으면 지원하지 않는 벤더가 예외를 던지는 메서드를 갖게 되고, 그러면 "지원하는가"를 호출해 보기 전에는
 * 알 수 없다. 포트를 나누면 <b>구현 존재 여부가 곧 능력 조회</b>다.
 */
public interface LlmBatchClientPort {

    AiVendor vendor();

    /** 제출은 즉시 끝나고 배치 ID 만 돌아온다. 처리는 최대 24시간까지 걸린다. */
    String submit(LlmBatchSubmitRequest request);

    LlmBatchPollResult poll(LlmBatchPollRequest request);
}
