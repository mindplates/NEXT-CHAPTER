package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogDomainCommand;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;

public interface CreateCatalogDomainUseCase {

    CatalogDomain create(CreateCatalogDomainCommand command);
}
