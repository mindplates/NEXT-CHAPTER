package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.EmbeddingStatusView;

/**
 * 임베딩 모델 교체 후 전량을 다시 계산하는 경로.
 *
 * <p>이 경로를 P1.4 에서 함께 만드는 이유는, 모델을 관리 화면에서 교체할 수 있게 만든 순간
 * 재임베딩이 <i>언젠가 필요한 것</i>이 아니라 <b>반드시 일어나는 것</b>이 되기 때문이다. 경로가
 * 없으면 교체 기능이 거짓말이 된다.
 */
public interface ReembedTopicsUseCase {

    EmbeddingStatusView status();

    /**
     * 남은 대상 중 최대 {@code batchSize} 개를 다시 임베딩한다.
     *
     * <p>전량을 한 번에 돌리지 않는 이유는 임베딩이 외부 호출이고 주제 수만큼 비용·시간이 들기
     * 때문이다. 반환값의 {@code outdated} 가 0이 될 때까지 반복 호출하는 형태로 쓴다 — 중간에
     * 끊겨도 다음 호출이 남은 것부터 이어간다.
     */
    EmbeddingStatusView reembedBatch(int batchSize);
}
