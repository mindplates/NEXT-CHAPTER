package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogFieldCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogFieldPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogFieldService
        implements CreateCatalogFieldUseCase, UpdateCatalogFieldUseCase, DeleteCatalogFieldUseCase {

    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final SaveCatalogFieldPort saveCatalogFieldPort;
    private final DeleteCatalogFieldPort deleteCatalogFieldPort;
    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;

    @Override
    public CatalogField create(CreateCatalogFieldCommand command) {
        if (loadCatalogDomainPort.findById(command.domainId()).isEmpty()) {
            throw new EntityNotFoundException("도메인", command.domainId());
        }
        CatalogField field = CatalogField.create(
                command.domainId(), command.slug(), command.name(), command.description(), command.sortOrder());
        loadCatalogFieldPort
                .findByDomainIdAndSlug(field.domainId(), field.slug())
                .ifPresent(existing -> {
                    throw new DuplicateException("분야", "slug", field.slug());
                });
        return saveCatalogFieldPort.save(field);
    }

    @Override
    public CatalogField update(Long id, UpdateCatalogNodeCommand command) {
        CatalogField field = loadCatalogFieldPort.findById(id).orElseThrow(() -> new EntityNotFoundException("분야", id));
        return saveCatalogFieldPort.save(field.withDetails(command.name(), command.description(), command.sortOrder()));
    }

    @Override
    public void delete(Long id) {
        if (loadCatalogFieldPort.findById(id).isEmpty()) {
            throw new EntityNotFoundException("분야", id);
        }
        long topicCount = loadCatalogTopicPort.countByFieldId(id);
        if (topicCount > 0) {
            throw new InvalidOperationException("주제가 " + topicCount + "개 남아 있어 분야를 삭제할 수 없습니다.");
        }
        deleteCatalogFieldPort.deleteById(id);
    }
}
