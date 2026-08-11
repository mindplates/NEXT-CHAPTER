package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogDomainJpaRepository extends JpaRepository<CatalogDomainJpaEntity, Long> {

    List<CatalogDomainJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    Optional<CatalogDomainJpaEntity> findBySlug(String slug);
}
