package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.TopicInputLogView;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.List;

/**
 * 미매핑 입력 검토 (P1.6).
 *
 * <p>여기가 카탈로그의 상위 계층이 늘어나는 유일한 경로다. 신규 도메인·분야를 자동으로 만들지 않는
 * 이유는 층별 좁히기가 사람의 판단 없이는 유지되지 않기 때문이다 — 입력마다 분야를 만들면 후보가
 * 다시 수백 개가 되고, 3단계로 나눈 이유가 사라진다.
 */
public interface ReviewTopicInputUseCase {

    PagedResult<TopicInputLogView> list(
            List<TopicInputResolution> resolutions, boolean onlyUnreviewed, int page, int size);

    /** 검토 완료로 표시한다. 카탈로그 반영은 별도 CRUD 로 하고, 여기서는 처리 여부만 남긴다. */
    TopicInputLogView markReviewed(Long id, String reviewer, String note);
}
