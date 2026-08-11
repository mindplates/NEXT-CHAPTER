package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogTopicPort;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogTopicService
        implements CreateCatalogTopicUseCase, UpdateCatalogTopicUseCase, DeleteCatalogTopicUseCase {

    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final SaveCatalogTopicPort saveCatalogTopicPort;
    private final DeleteCatalogTopicPort deleteCatalogTopicPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final LoadSkeletonPort loadSkeletonPort;

    @Override
    public CatalogTopic create(CreateCatalogTopicCommand command) {
        if (loadCatalogFieldPort.findById(command.fieldId()).isEmpty()) {
            throw new EntityNotFoundException("분야", command.fieldId());
        }
        CatalogTopic topic = CatalogTopic.create(
                command.fieldId(), command.slug(), command.name(), command.description(), command.sortOrder());
        loadCatalogTopicPort.findBySlug(topic.slug()).ifPresent(existing -> {
            throw new DuplicateException("주제", "slug", topic.slug());
        });
        return saveCatalogTopicPort.save(topic);
    }

    @Override
    public CatalogTopic update(Long id, UpdateCatalogNodeCommand command) {
        CatalogTopic topic = loadCatalogTopicPort.findById(id).orElseThrow(() -> new EntityNotFoundException("주제", id));
        return saveCatalogTopicPort.save(topic.withDetails(command.name(), command.description(), command.sortOrder()));
    }

    /**
     * 뼈대가 붙은 주제는 삭제하지 않는다. 뼈대에는 챕터 본문과 그 위에 쌓인 집단 신호가 딸려
     * 있어서, 주제를 지우는 것은 개선 루프의 근거 데이터를 지우는 일이 된다. 정리가 필요하면
     * 뼈대 쪽에서 명시적으로 다뤄야 한다.
     */
    @Override
    public void delete(Long id) {
        if (loadCatalogTopicPort.findById(id).isEmpty()) {
            throw new EntityNotFoundException("주제", id);
        }
        if (loadSkeletonPort.findByTopicId(id).isPresent()) {
            throw new InvalidOperationException("뼈대가 붙어 있어 주제를 삭제할 수 없습니다.");
        }
        deleteCatalogTopicPort.deleteById(id);
    }
}
