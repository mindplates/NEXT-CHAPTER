package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.List;
import java.util.Optional;

public interface LoadTopicInputLogPort {

    Optional<TopicInputLog> findById(Long id);

    /**
     * 검토 대기열. 상태와 검토 여부로 걸러 페이지 단위로 본다.
     *
     * @param resolutions 볼 상태. 비어 있으면 전체
     * @param onlyUnreviewed 미검토만
     */
    PagedResult<TopicInputLog> search(
            List<TopicInputResolution> resolutions, boolean onlyUnreviewed, int page, int size);

    /** 같은 정규화 표현이 몇 번 들어왔는지. 반복되는 미매핑 입력이 카탈로그의 빈 곳을 가리킨다. */
    long countByNormalized(String normalized);
}
