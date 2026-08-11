package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogDomainCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogDomainPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogDomainService
        implements CreateCatalogDomainUseCase, UpdateCatalogDomainUseCase, DeleteCatalogDomainUseCase {

    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final SaveCatalogDomainPort saveCatalogDomainPort;
    private final DeleteCatalogDomainPort deleteCatalogDomainPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;

    @Override
    public CatalogDomain create(CreateCatalogDomainCommand command) {
        CatalogDomain domain =
                CatalogDomain.create(command.slug(), command.name(), command.description(), command.sortOrder());
        loadCatalogDomainPort.findBySlug(domain.slug()).ifPresent(existing -> {
            throw new DuplicateException("도메인", "slug", domain.slug());
        });
        return saveCatalogDomainPort.save(domain);
    }

    @Override
    public CatalogDomain update(Long id, UpdateCatalogNodeCommand command) {
        CatalogDomain domain =
                loadCatalogDomainPort.findById(id).orElseThrow(() -> new EntityNotFoundException("도메인", id));
        return saveCatalogDomainPort.save(
                domain.withDetails(command.name(), command.description(), command.sortOrder()));
    }

    /**
     * 자식이 남아 있으면 삭제하지 않는다. FK 로도 막히지만 그때는 드라이버 예외가 500 으로 나가고
     * 운영자는 무엇이 걸렸는지 알 수 없다. 이유를 아는 곳에서 먼저 막는다.
     */
    @Override
    public void delete(Long id) {
        if (loadCatalogDomainPort.findById(id).isEmpty()) {
            throw new EntityNotFoundException("도메인", id);
        }
        long fieldCount = loadCatalogFieldPort.countByDomainId(id);
        if (fieldCount > 0) {
            throw new InvalidOperationException("분야가 " + fieldCount + "개 남아 있어 도메인을 삭제할 수 없습니다.");
        }
        deleteCatalogDomainPort.deleteById(id);
    }
}
