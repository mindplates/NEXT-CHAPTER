package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicAliasPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TopicAliasService implements ManageTopicAliasUseCase {

    private final LoadTopicAliasPort loadTopicAliasPort;
    private final SaveTopicAliasPort saveTopicAliasPort;
    private final DeleteTopicAliasPort deleteTopicAliasPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;

    /**
     * 같은 정규화 표현이 이미 있으면 거절한다. 한 표현이 두 주제를 가리키면 그 입력이 어느 뼈대로
     * 갈지 알 수 없고, 그 모호함은 런타임에 에러 없이 신호를 반으로 가른다.
     */
    @Override
    public TopicAlias add(Long topicId, String alias) {
        if (loadCatalogTopicPort.findById(topicId).isEmpty()) {
            throw new EntityNotFoundException("주제", topicId);
        }
        TopicAlias candidate = TopicAlias.create(topicId, alias);
        loadTopicAliasPort.findByNormalized(candidate.normalized()).ifPresent(existing -> {
            throw new DuplicateException("별칭 '" + alias + "' 은(는) 이미 다른 주제(ID: " + existing.topicId() + ")에 등록돼 있습니다.");
        });
        return saveTopicAliasPort.save(candidate);
    }

    @Override
    public void remove(Long aliasId) {
        if (loadTopicAliasPort.findById(aliasId).isEmpty()) {
            throw new EntityNotFoundException("별칭", aliasId);
        }
        deleteTopicAliasPort.deleteById(aliasId);
    }
}
