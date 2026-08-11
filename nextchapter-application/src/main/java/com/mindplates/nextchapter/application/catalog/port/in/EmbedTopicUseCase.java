package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;

public interface EmbedTopicUseCase {

    /** 주제 하나를 지금 설정된 모델로 임베딩해 저장한다. 이미 있으면 덮어쓴다. */
    TopicEmbedding embed(Long topicId);
}
