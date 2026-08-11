package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogFieldJpaRepository extends JpaRepository<CatalogFieldJpaEntity, Long> {

    List<CatalogFieldJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    List<CatalogFieldJpaEntity> findByDomainIdOrderBySortOrderAscNameAsc(Long domainId);

    Optional<CatalogFieldJpaEntity> findByDomainIdAndSlug(Long domainId, String slug);

    long countByDomainId(Long domainId);
}
