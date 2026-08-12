package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.SeedCatalogCommand;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.view.CatalogSeedView;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 시드가 지켜야 하는 것은 <b>멱등성</b>이다. 재기동마다 같은 행을 다시 만들면 슬러그 UNIQUE 에 걸려 기동이
 * 실패하거나(운이 좋은 경우) 카탈로그가 갈라진다. 갱신 경로가 없는 것도 함께 고정한다 — 시드로 운영 중
 * 카탈로그를 덮으면 이미 쌓인 입력 로그·별칭과 어긋나 조용히 매핑이 틀린다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("카탈로그 시드")
class CatalogSeedServiceTest {

    @Mock
    LoadCatalogDomainPort loadCatalogDomainPort;

    @Mock
    LoadCatalogFieldPort loadCatalogFieldPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadTopicAliasPort loadTopicAliasPort;

    @Mock
    CreateCatalogDomainUseCase createCatalogDomainUseCase;

    @Mock
    CreateCatalogFieldUseCase createCatalogFieldUseCase;

    @Mock
    CreateCatalogTopicUseCase createCatalogTopicUseCase;

    @Mock
    ManageTopicAliasUseCase manageTopicAliasUseCase;

    @InjectMocks
    CatalogSeedService service;

    private static final CatalogDomain DOMAIN = new CatalogDomain(1L, "computer-science", "컴퓨터과학", null, 0, null, null);
    private static final CatalogField FIELD =
            new CatalogField(2L, 1L, "artificial-intelligence", "인공지능", null, 0, null, null);
    private static final CatalogTopic TOPIC = new CatalogTopic(3L, 2L, "machine-learning", "머신러닝", null, 0, null, null);

    private static SeedCatalogCommand command() {
        return new SeedCatalogCommand(List.of(new SeedCatalogCommand.DomainSeed(
                "computer-science",
                "컴퓨터과학",
                null,
                List.of(new SeedCatalogCommand.FieldSeed(
                        "artificial-intelligence",
                        "인공지능",
                        null,
                        List.of(new SeedCatalogCommand.TopicSeed(
                                "machine-learning", "머신러닝", null, List.of("기계학습", "ML"))))))));
    }

    @Test
    @DisplayName("비어 있으면 네 층을 모두 만든다")
    void createsAllLayers() {
        when(loadCatalogDomainPort.findBySlug("computer-science")).thenReturn(Optional.empty());
        when(createCatalogDomainUseCase.create(any())).thenReturn(DOMAIN);
        when(loadCatalogFieldPort.findByDomainIdAndSlug(1L, "artificial-intelligence"))
                .thenReturn(Optional.empty());
        when(createCatalogFieldUseCase.create(any())).thenReturn(FIELD);
        when(loadCatalogTopicPort.findBySlug("machine-learning")).thenReturn(Optional.empty());
        when(createCatalogTopicUseCase.create(any())).thenReturn(TOPIC);
        when(loadTopicAliasPort.findByNormalized(anyString())).thenReturn(Optional.empty());

        CatalogSeedView view = service.seed(command());

        assertThat(view.domainsCreated()).isEqualTo(1);
        assertThat(view.fieldsCreated()).isEqualTo(1);
        assertThat(view.topicsCreated()).isEqualTo(1);
        assertThat(view.aliasesCreated()).isEqualTo(2);
        verify(manageTopicAliasUseCase).add(3L, "기계학습");
        verify(manageTopicAliasUseCase).add(3L, "ML");
    }

    /** 두 번째 기동에서 아무것도 만들지 않아야 한다. 만들면 슬러그 UNIQUE 에 걸린다. */
    @Test
    @DisplayName("이미 있으면 아무것도 만들지 않는다")
    void isIdempotent() {
        when(loadCatalogDomainPort.findBySlug("computer-science")).thenReturn(Optional.of(DOMAIN));
        when(loadCatalogFieldPort.findByDomainIdAndSlug(1L, "artificial-intelligence"))
                .thenReturn(Optional.of(FIELD));
        when(loadCatalogTopicPort.findBySlug("machine-learning")).thenReturn(Optional.of(TOPIC));
        when(loadTopicAliasPort.findByNormalized(anyString()))
                .thenReturn(Optional.of(new TopicAlias(9L, 3L, "기계학습", null)));

        CatalogSeedView view = service.seed(command());

        assertThat(view.createdNothing()).isTrue();
        verify(createCatalogDomainUseCase, never()).create(any());
        verify(createCatalogFieldUseCase, never()).create(any());
        verify(createCatalogTopicUseCase, never()).create(any());
        verify(manageTopicAliasUseCase, never()).add(anyLong(), anyString());
    }

    /**
     * 같은 표현이 두 주제를 가리키면 그 입력은 어느 뼈대로 갈지 알 수 없다. 그 모호함을 시드가 만들어서는
     * 안 되므로, 이미 있는 별칭은 <b>다른 주제에 붙어 있어도</b> 건드리지 않는다.
     */
    @Test
    @DisplayName("이미 쓰인 별칭은 다른 주제에 붙어 있어도 건드리지 않는다")
    void doesNotStealAlias() {
        when(loadCatalogDomainPort.findBySlug("computer-science")).thenReturn(Optional.of(DOMAIN));
        when(loadCatalogFieldPort.findByDomainIdAndSlug(1L, "artificial-intelligence"))
                .thenReturn(Optional.of(FIELD));
        when(loadCatalogTopicPort.findBySlug("machine-learning")).thenReturn(Optional.of(TOPIC));
        when(loadTopicAliasPort.findByNormalized("기계학습"))
                .thenReturn(Optional.of(new TopicAlias(9L, 99L, "기계학습", null)));
        when(loadTopicAliasPort.findByNormalized("ml")).thenReturn(Optional.empty());

        CatalogSeedView view = service.seed(command());

        assertThat(view.aliasesCreated()).isEqualTo(1);
        verify(manageTopicAliasUseCase).add(3L, "ML");
        verify(manageTopicAliasUseCase, never()).add(3L, "기계학습");
    }

    /** 주제 없는 분야는 정상이다 — 분야는 매핑의 발판이고, 주제는 뼈대가 붙는 지점이라 소수여야 한다. */
    @Test
    @DisplayName("주제 없는 분야도 만들어진다")
    void createsFieldWithoutTopics() {
        when(loadCatalogDomainPort.findBySlug("computer-science")).thenReturn(Optional.of(DOMAIN));
        when(loadCatalogFieldPort.findByDomainIdAndSlug(1L, "databases")).thenReturn(Optional.empty());
        when(createCatalogFieldUseCase.create(any()))
                .thenReturn(new CatalogField(4L, 1L, "databases", "데이터베이스", null, 3, null, null));

        CatalogSeedView view = service.seed(new SeedCatalogCommand(List.of(new SeedCatalogCommand.DomainSeed(
                "computer-science",
                "컴퓨터과학",
                null,
                List.of(new SeedCatalogCommand.FieldSeed("databases", "데이터베이스", null, null))))));

        assertThat(view.fieldsCreated()).isEqualTo(1);
        assertThat(view.topicsCreated()).isZero();
    }

    @Test
    @DisplayName("빈 시드는 아무것도 하지 않는다")
    void emptySeedDoesNothing() {
        assertThat(service.seed(new SeedCatalogCommand(null)).createdNothing()).isTrue();
    }
}
