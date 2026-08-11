package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;

public interface UpdateCatalogDomainUseCase {

    CatalogDomain update(Long id, UpdateCatalogNodeCommand command);
}
