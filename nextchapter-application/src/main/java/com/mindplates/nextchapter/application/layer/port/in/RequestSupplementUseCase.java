package com.mindplates.nextchapter.application.layer.port.in;

import com.mindplates.nextchapter.application.layer.view.SupplementView;

/**
 * 막힌 지점에 보충 설명을 붙인다.
 *
 * <p><b>요청 시 즉시 생성한다.</b> 사전 생성은 아무도 막히지 않은 지점의 설명까지 만들어야 하고, 그 비용이
 * 블록 수 × 사용자 수로 늘어난다. 대가는 사용자가 잠깐 기다린다는 점이다.
 *
 * <p>같은 지점을 다시 요청하면 <b>이미 만든 것을 돌려준다.</b> 그것이 "조회마다 생성하면 비용이 사용자 수 ×
 * 조회 수로 늘어난다"는 리스크의 대응이다 — 레이어가 곧 캐시다.
 */
public interface RequestSupplementUseCase {

    SupplementView request(Long userId, Long chapterId, String anchorBlockId);
}
