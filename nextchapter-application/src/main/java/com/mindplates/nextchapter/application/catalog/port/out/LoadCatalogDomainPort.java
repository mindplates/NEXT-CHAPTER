package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import java.util.List;
import java.util.Optional;

public interface LoadCatalogDomainPort {

    List<CatalogDomain> findAll();

    Optional<CatalogDomain> findById(Long id);

    Optional<CatalogDomain> findBySlug(String slug);
}
