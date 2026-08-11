package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogFieldCommand;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;

public interface CreateCatalogFieldUseCase {

    CatalogField create(CreateCatalogFieldCommand command);
}
