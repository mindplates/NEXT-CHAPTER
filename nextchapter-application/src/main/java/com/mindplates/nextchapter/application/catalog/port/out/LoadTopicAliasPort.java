package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;
import java.util.Optional;

public interface LoadTopicAliasPort {

    List<TopicAlias> findByTopicId(Long topicId);

    Optional<TopicAlias> findById(Long id);

    /** 정규화된 표현으로 찾는다. 자유 입력 매핑의 첫 관문 — 여기서 맞으면 LLM 을 부르지 않는다. */
    Optional<TopicAlias> findByNormalized(String normalized);
}
