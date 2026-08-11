package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;

public interface SaveCatalogDomainPort {

    CatalogDomain save(CatalogDomain domain);
}
