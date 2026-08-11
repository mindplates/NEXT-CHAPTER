package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogFieldPort;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CatalogFieldPersistenceAdapter
        implements LoadCatalogFieldPort, SaveCatalogFieldPort, DeleteCatalogFieldPort {

    private final CatalogFieldJpaRepository fieldRepository;
    private final CatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogField> findAll() {
        return fieldRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(mapper::fieldToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogField> findByDomainId(Long domainId) {
        return fieldRepository.findByDomainIdOrderBySortOrderAscNameAsc(domainId).stream()
                .map(mapper::fieldToDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogField> findById(Long id) {
        return fieldRepository.findById(id).map(mapper::fieldToDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogField> findByDomainIdAndSlug(Long domainId, String slug) {
        return fieldRepository.findByDomainIdAndSlug(domainId, slug).map(mapper::fieldToDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDomainId(Long domainId) {
        return fieldRepository.countByDomainId(domainId);
    }

    @Override
    @Transactional
    public CatalogField save(CatalogField field) {
        return mapper.fieldToDomain(fieldRepository.save(mapper.fieldToEntity(field)));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        fieldRepository.deleteById(id);
    }
}
