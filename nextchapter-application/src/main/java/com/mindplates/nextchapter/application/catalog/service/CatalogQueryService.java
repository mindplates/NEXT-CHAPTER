package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.view.CatalogTreeView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService implements GetCatalogUseCase {

    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadTopicAliasPort loadTopicAliasPort;
    private final LoadSkeletonPort loadSkeletonPort;

    @Override
    public List<CatalogDomain> domains() {
        return loadCatalogDomainPort.findAll();
    }

    @Override
    public CatalogDomain domain(Long id) {
        return loadCatalogDomainPort.findById(id).orElseThrow(() -> new EntityNotFoundException("도메인", id));
    }

    @Override
    public List<CatalogField> fields(Long domainId) {
        return loadCatalogFieldPort.findByDomainId(domainId);
    }

    @Override
    public CatalogField field(Long id) {
        return loadCatalogFieldPort.findById(id).orElseThrow(() -> new EntityNotFoundException("분야", id));
    }

    @Override
    public List<CatalogTopic> topics(Long fieldId) {
        return loadCatalogTopicPort.findByFieldId(fieldId);
    }

    @Override
    public CatalogTopic topic(Long id) {
        return loadCatalogTopicPort.findById(id).orElseThrow(() -> new EntityNotFoundException("주제", id));
    }

    @Override
    public List<TopicAlias> aliases(Long topicId) {
        return loadTopicAliasPort.findByTopicId(topicId);
    }

    /**
     * 세 계층을 각각 한 번씩만 읽고 메모리에서 조립한다. 계층별로 자식을 다시 조회하면 도메인
     * 수 × 분야 수만큼 쿼리가 붙는다 — 카탈로그는 관리 화면과 매핑 프롬프트 양쪽에서 자주 읽히는
     * 데이터라 그 비용이 그대로 응답 시간이 된다.
     */
    @Override
    public CatalogTreeView tree() {
        List<CatalogDomain> domains = loadCatalogDomainPort.findAll();
        Map<Long, List<CatalogField>> fieldsByDomain =
                loadCatalogFieldPort.findAll().stream().collect(Collectors.groupingBy(CatalogField::domainId));
        Map<Long, List<CatalogTopic>> topicsByField =
                loadCatalogTopicPort.findAll().stream().collect(Collectors.groupingBy(CatalogTopic::fieldId));
        Set<Long> topicIdsWithSkeleton = Set.copyOf(loadSkeletonPort.findTopicIdsWithSkeleton());

        List<CatalogTreeView.DomainNode> nodes = domains.stream()
                .map(domain -> new CatalogTreeView.DomainNode(
                        domain.id(),
                        domain.slug(),
                        domain.name(),
                        domain.description(),
                        toFieldNodes(
                                fieldsByDomain.getOrDefault(domain.id(), List.of()),
                                topicsByField,
                                topicIdsWithSkeleton)))
                .toList();
        return new CatalogTreeView(nodes);
    }

    private static List<CatalogTreeView.FieldNode> toFieldNodes(
            List<CatalogField> fields, Map<Long, List<CatalogTopic>> topicsByField, Set<Long> topicIdsWithSkeleton) {
        return fields.stream()
                .map(field -> new CatalogTreeView.FieldNode(
                        field.id(),
                        field.slug(),
                        field.name(),
                        field.description(),
                        toTopicNodes(topicsByField.getOrDefault(field.id(), List.of()), topicIdsWithSkeleton)))
                .toList();
    }

    private static List<CatalogTreeView.TopicNode> toTopicNodes(
            List<CatalogTopic> topics, Set<Long> topicIdsWithSkeleton) {
        return topics.stream()
                .map(topic -> new CatalogTreeView.TopicNode(
                        topic.id(),
                        topic.slug(),
                        topic.name(),
                        topic.description(),
                        topicIdsWithSkeleton.contains(topic.id())))
                .toList();
    }
}
