package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogTopicPort;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CatalogTopicPersistenceAdapter
        implements LoadCatalogTopicPort, SaveCatalogTopicPort, DeleteCatalogTopicPort {

    private final CatalogTopicJpaRepository topicRepository;
    private final CatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogTopic> findAll() {
        return topicRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(mapper::topicToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogTopic> findByFieldId(Long fieldId) {
        return topicRepository.findByFieldIdOrderBySortOrderAscNameAsc(fieldId).stream()
                .map(mapper::topicToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogTopic> findByFieldIdIn(List<Long> fieldIds) {
        if (fieldIds.isEmpty()) {
            return List.of();
        }
        return topicRepository.findByFieldIdInOrderBySortOrderAscNameAsc(fieldIds).stream()
                .map(mapper::topicToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogTopic> findById(Long id) {
        return topicRepository.findById(id).map(mapper::topicToDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogTopic> findBySlug(String slug) {
        return topicRepository.findBySlug(slug).map(mapper::topicToDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFieldId(Long fieldId) {
        return topicRepository.countByFieldId(fieldId);
    }

    @Override
    @Transactional
    public CatalogTopic save(CatalogTopic topic) {
        return mapper.topicToDomain(topicRepository.save(mapper.topicToEntity(topic)));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        topicRepository.deleteById(id);
    }
}
