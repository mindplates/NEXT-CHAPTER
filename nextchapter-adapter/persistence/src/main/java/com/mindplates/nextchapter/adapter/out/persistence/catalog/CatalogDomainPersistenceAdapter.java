package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogDomainPort;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어댑터를 계층마다 하나씩 둔다.
 *
 * <p>넷을 한 클래스로 합칠 수는 없다 — 세 계층의 {@code findById(Long)} 이 시그니처는 같고 반환
 * 타입만 달라 한 클래스가 동시에 구현할 수 없고, {@code deleteById(Long)} 는 셋이 완전히 같은
 * 시그니처라 합치면 어느 계층을 지우는지가 사라진다. 뒤쪽이 더 위험하다 — 컴파일은 통과하고
 * 삭제 대상만 조용히 하나로 줄어든다.
 */
@Component
@RequiredArgsConstructor
public class CatalogDomainPersistenceAdapter
        implements LoadCatalogDomainPort, SaveCatalogDomainPort, DeleteCatalogDomainPort {

    private final CatalogDomainJpaRepository domainRepository;
    private final CatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogDomain> findAll() {
        return domainRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogDomain> findById(Long id) {
        return domainRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogDomain> findBySlug(String slug) {
        return domainRepository.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public CatalogDomain save(CatalogDomain domain) {
        return mapper.toDomain(domainRepository.save(mapper.toEntity(domain)));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        domainRepository.deleteById(id);
    }
}
