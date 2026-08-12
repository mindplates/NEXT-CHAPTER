package com.mindplates.nextchapter.domain.chapter.model;

/**
 * 딜리버리 형태. 같은 블록 문서를 형태별로 다르게 해석한다.
 *
 * <p>형태를 <b>신호에도 함께 기록한다.</b> 영상이 잘 만들어져 영상 시청자는 맞추고 웹 독자는 틀린다면,
 * 둘을 합친 오답률은 중간값이 되어 웹 콘텐츠의 결함이 가려진다. 다만 처리는 비대칭이다 — 임계치 판정은
 * 형태 구분 없이 통합 집계로 하고(나누면 초기 규모에서 어느 쪽도 임계치를 못 넘는다), 수정안의 근거로만
 * 형태별 분해를 붙인다.
 *
 * <p>{@code WEB} 만 {@link BlockType#NARRATION} 을 걸러 낸다. 그 블록은 음성·자막의 원천이라 화면에
 * 그리면 같은 내용이 두 번 보인다. PPT 에서는 발표자 노트로 쓸 수 있어 걸러 내지 않는다.
 */
public enum DeliveryFormat {
    WEB,
    VIDEO,
    PRESENTATION;

    /** 이 형태의 렌더러가 이 블록을 쓰는지. */
    public boolean includes(BlockType type) {
        return this != WEB || !type.isDeliverySpecific();
    }
}
