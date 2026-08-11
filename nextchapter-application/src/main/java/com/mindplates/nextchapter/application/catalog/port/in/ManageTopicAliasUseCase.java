package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;

public interface ManageTopicAliasUseCase {

    TopicAlias add(Long topicId, String alias);

    void remove(Long aliasId);
}
