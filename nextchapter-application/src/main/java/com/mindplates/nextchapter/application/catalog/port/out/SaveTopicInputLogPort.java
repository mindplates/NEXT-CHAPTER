package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;

public interface SaveTopicInputLogPort {

    TopicInputLog save(TopicInputLog log);
}
