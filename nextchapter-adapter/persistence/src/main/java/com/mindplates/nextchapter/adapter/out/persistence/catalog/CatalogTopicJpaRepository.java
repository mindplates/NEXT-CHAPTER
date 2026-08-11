package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogTopicJpaRepository extends JpaRepository<CatalogTopicJpaEntity, Long> {

    List<CatalogTopicJpaEntity> findAllByOrderBySortOrderAscNameAsc();

    List<CatalogTopicJpaEntity> findByFieldIdOrderBySortOrderAscNameAsc(Long fieldId);

    List<CatalogTopicJpaEntity> findByFieldIdInOrderBySortOrderAscNameAsc(Collection<Long> fieldIds);

    Optional<CatalogTopicJpaEntity> findBySlug(String slug);

    long countByFieldId(Long fieldId);
}
