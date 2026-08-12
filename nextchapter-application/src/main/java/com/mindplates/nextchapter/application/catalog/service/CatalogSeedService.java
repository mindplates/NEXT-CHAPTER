package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SeedCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogDomainCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogFieldCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.SeedCatalogCommand;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.view.CatalogSeedView;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 카탈로그 투입 — <b>멱등</b>하다.
 *
 * <p>슬러그로 존재를 확인하고 없을 때만 만든다. 갱신·삭제 경로를 두지 않은 이유는, 시드 파일을 고쳐 운영
 * 중 카탈로그를 덮을 수 있게 되면 그 덮임이 이미 쌓인 사용자 입력 로그·별칭과 어긋나 <b>조용히 매핑을
 * 틀리게</b> 하기 때문이다.
 *
 * <p>뼈대 생성을 걸지 않는다. 시드 주제 하나가 곧 뼈대 하나이고 뼈대 하나가 전 범위 생성 비용이므로,
 * 기동마다 생성이 걸리면 재기동이 곧 비용이 된다. 임베딩도 부르지 않는다 — 둘 다 벤더 키를 쓰는 일이고,
 * 시작 시점에 조용히 비용이 나가는 경로를 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogSeedService implements SeedCatalogUseCase {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedService.class);

    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadTopicAliasPort loadTopicAliasPort;
    private final CreateCatalogDomainUseCase createCatalogDomainUseCase;
    private final CreateCatalogFieldUseCase createCatalogFieldUseCase;
    private final CreateCatalogTopicUseCase createCatalogTopicUseCase;
    private final ManageTopicAliasUseCase manageTopicAliasUseCase;

    @Override
    public CatalogSeedView seed(SeedCatalogCommand command) {
        Counters counters = new Counters();
        int domainOrder = 0;
        for (SeedCatalogCommand.DomainSeed domainSeed : command.domains()) {
            CatalogDomain domain = domain(domainSeed, domainOrder++, counters);
            int fieldOrder = 0;
            for (SeedCatalogCommand.FieldSeed fieldSeed : domainSeed.fields()) {
                CatalogField field = field(domain, fieldSeed, fieldOrder++, counters);
                int topicOrder = 0;
                for (SeedCatalogCommand.TopicSeed topicSeed : fieldSeed.topics()) {
                    topic(field, topicSeed, topicOrder++, counters);
                }
            }
        }
        CatalogSeedView view =
                new CatalogSeedView(counters.domains, counters.fields, counters.topics, counters.aliases);
        if (view.createdNothing()) {
            log.info("[카탈로그 시드] 이미 전부 있어 아무것도 만들지 않았다.");
        } else {
            log.info(
                    "[카탈로그 시드] 도메인 {} · 분야 {} · 주제 {} · 별칭 {} 생성",
                    view.domainsCreated(),
                    view.fieldsCreated(),
                    view.topicsCreated(),
                    view.aliasesCreated());
        }
        return view;
    }

    private CatalogDomain domain(SeedCatalogCommand.DomainSeed seed, int order, Counters counters) {
        return loadCatalogDomainPort.findBySlug(seed.slug()).orElseGet(() -> {
            counters.domains++;
            return createCatalogDomainUseCase.create(
                    new CreateCatalogDomainCommand(seed.slug(), seed.name(), seed.description(), order));
        });
    }

    private CatalogField field(CatalogDomain domain, SeedCatalogCommand.FieldSeed seed, int order, Counters counters) {
        return loadCatalogFieldPort
                .findByDomainIdAndSlug(domain.id(), seed.slug())
                .orElseGet(() -> {
                    counters.fields++;
                    return createCatalogFieldUseCase.create(new CreateCatalogFieldCommand(
                            domain.id(), seed.slug(), seed.name(), seed.description(), order));
                });
    }

    private void topic(CatalogField field, SeedCatalogCommand.TopicSeed seed, int order, Counters counters) {
        CatalogTopic topic = loadCatalogTopicPort.findBySlug(seed.slug()).orElseGet(() -> {
            counters.topics++;
            return createCatalogTopicUseCase.create(
                    new CreateCatalogTopicCommand(field.id(), seed.slug(), seed.name(), seed.description(), order));
        });
        seed.aliases().forEach(alias -> alias(topic, alias, counters));
    }

    /**
     * 별칭은 정규화 표현이 전역 유일이다. 이미 있으면 <b>어느 주제에 붙어 있든</b> 건드리지 않는다 — 같은
     * 표현이 두 주제를 가리키면 그 입력은 어느 뼈대로 갈지 알 수 없고, 그 모호함을 시드가 만들어서는 안 된다.
     */
    private void alias(CatalogTopic topic, String alias, Counters counters) {
        String normalized = Strings.normalizeForMatch(alias);
        if (normalized == null
                || loadTopicAliasPort.findByNormalized(normalized).isPresent()) {
            return;
        }
        manageTopicAliasUseCase.add(topic.id(), alias);
        counters.aliases++;
    }

    /** 생성 건수만 세는 가변 카운터. 결과를 만들기 위한 지역 상태이므로 밖으로 나가지 않는다. */
    private static final class Counters {
        private int domains;
        private int fields;
        private int topics;
        private int aliases;
    }
}
