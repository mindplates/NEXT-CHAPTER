package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;

public interface CreateCatalogTopicUseCase {

    CatalogTopic create(CreateCatalogTopicCommand command);
}
