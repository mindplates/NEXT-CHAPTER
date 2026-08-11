package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;

public interface SaveTopicAliasPort {

    TopicAlias save(TopicAlias alias);
}
