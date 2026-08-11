package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicAliasJpaRepository extends JpaRepository<TopicAliasJpaEntity, Long> {

    List<TopicAliasJpaEntity> findByTopicIdOrderByAliasAsc(Long topicId);

    Optional<TopicAliasJpaEntity> findByNormalized(String normalized);
}
