package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import java.util.List;
import java.util.Optional;

public interface LoadCatalogFieldPort {

    List<CatalogField> findAll();

    List<CatalogField> findByDomainId(Long domainId);

    Optional<CatalogField> findById(Long id);

    Optional<CatalogField> findByDomainIdAndSlug(Long domainId, String slug);

    long countByDomainId(Long domainId);
}
