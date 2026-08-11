package com.mindplates.nextchapter.application.catalog.port.out;

import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import java.util.List;
import java.util.Optional;

public interface LoadCatalogTopicPort {

    List<CatalogTopic> findAll();

    List<CatalogTopic> findByFieldId(Long fieldId);

    List<CatalogTopic> findByFieldIdIn(List<Long> fieldIds);

    Optional<CatalogTopic> findById(Long id);

    Optional<CatalogTopic> findBySlug(String slug);

    long countByFieldId(Long fieldId);
}
