package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicAliasPort;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TopicAliasPersistenceAdapter implements LoadTopicAliasPort, SaveTopicAliasPort, DeleteTopicAliasPort {

    private final TopicAliasJpaRepository aliasRepository;
    private final CatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TopicAlias> findByTopicId(Long topicId) {
        return aliasRepository.findByTopicIdOrderByAliasAsc(topicId).stream()
                .map(mapper::aliasToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicAlias> findById(Long id) {
        return aliasRepository.findById(id).map(mapper::aliasToDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicAlias> findByNormalized(String normalized) {
        if (normalized == null) {
            return Optional.empty();
        }
        return aliasRepository.findByNormalized(normalized).map(mapper::aliasToDomain);
    }

    @Override
    @Transactional
    public TopicAlias save(TopicAlias alias) {
        return mapper.aliasToDomain(aliasRepository.save(mapper.aliasToEntity(alias)));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        aliasRepository.deleteById(id);
    }
}
