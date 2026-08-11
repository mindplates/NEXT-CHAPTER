package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;

public interface SaveTopicEmbeddingPort {

    /** 주제당 하나이므로 저장은 곧 덮어쓰기(upsert)다. */
    TopicEmbedding save(TopicEmbedding embedding);

    void deleteByTopicId(Long topicId);
}
