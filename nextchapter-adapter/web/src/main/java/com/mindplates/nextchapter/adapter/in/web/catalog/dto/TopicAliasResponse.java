package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.time.LocalDateTime;

public record TopicAliasResponse(Long id, Long topicId, String alias, String normalized, LocalDateTime createdAt) {

    public static TopicAliasResponse from(TopicAlias alias) {
        return new TopicAliasResponse(
                alias.id(), alias.topicId(), alias.alias(), alias.normalized(), alias.createdAt());
    }
}
