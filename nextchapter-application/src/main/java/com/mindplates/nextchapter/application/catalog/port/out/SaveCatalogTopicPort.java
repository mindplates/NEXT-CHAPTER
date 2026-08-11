package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;

public interface SaveCatalogTopicPort {

    CatalogTopic save(CatalogTopic topic);
}
