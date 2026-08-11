package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogField;

public interface SaveCatalogFieldPort {

    CatalogField save(CatalogField field);
}
