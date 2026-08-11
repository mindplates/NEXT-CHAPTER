package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import org.springframework.stereotype.Component;

@Component
public class CatalogPersistenceMapper {

    public CatalogDomain toDomain(CatalogDomainJpaEntity entity) {
        return new CatalogDomain(
                entity.getId(),
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public CatalogDomainJpaEntity toEntity(CatalogDomain domain) {
        return CatalogDomainJpaEntity.builder()
                .id(domain.id())
                .slug(domain.slug())
                .name(domain.name())
                .description(domain.description())
                .sortOrder(domain.sortOrder())
                .build();
    }

    public CatalogField fieldToDomain(CatalogFieldJpaEntity entity) {
        return new CatalogField(
                entity.getId(),
                entity.getDomainId(),
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public CatalogFieldJpaEntity fieldToEntity(CatalogField field) {
        return CatalogFieldJpaEntity.builder()
                .id(field.id())
                .domainId(field.domainId())
                .slug(field.slug())
                .name(field.name())
                .description(field.description())
                .sortOrder(field.sortOrder())
                .build();
    }

    public CatalogTopic topicToDomain(CatalogTopicJpaEntity entity) {
        return new CatalogTopic(
                entity.getId(),
                entity.getFieldId(),
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public CatalogTopicJpaEntity topicToEntity(CatalogTopic topic) {
        return CatalogTopicJpaEntity.builder()
                .id(topic.id())
                .fieldId(topic.fieldId())
                .slug(topic.slug())
                .name(topic.name())
                .description(topic.description())
                .sortOrder(topic.sortOrder())
                .build();
    }

    public TopicAlias aliasToDomain(TopicAliasJpaEntity entity) {
        return new TopicAlias(entity.getId(), entity.getTopicId(), entity.getAlias(), entity.getCreatedAt());
    }

    public TopicAliasJpaEntity aliasToEntity(TopicAlias alias) {
        return TopicAliasJpaEntity.builder()
                .id(alias.id())
                .topicId(alias.topicId())
                .alias(alias.alias())
                .normalized(alias.normalized())
                .build();
    }
}
