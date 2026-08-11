package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("카탈로그 조회")
class CatalogQueryServiceTest {

    @Mock
    LoadCatalogDomainPort loadCatalogDomainPort;

    @Mock
    LoadCatalogFieldPort loadCatalogFieldPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadTopicAliasPort loadTopicAliasPort;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @InjectMocks
    CatalogQueryService service;

    /**
     * 트리 조립이 계층별로 <b>한 번씩만</b> 읽는지를 확인한다. 자식을 부모마다 다시 조회하면 쿼리가
     * 도메인 수 × 분야 수만큼 붙고, 카탈로그는 관리 화면과 매핑 프롬프트 양쪽에서 자주 읽히는
     * 데이터라 그 비용이 그대로 응답 시간이 된다.
     */
    @Test
    @DisplayName("계층별로 한 번씩만 조회해 메모리에서 조립한다")
    void treeReadsEachLevelOnce() {
        when(loadCatalogDomainPort.findAll()).thenReturn(List.of(domain(1L, "cs"), domain(2L, "math")));
        when(loadCatalogFieldPort.findAll()).thenReturn(List.of(field(10L, 1L, "ai"), field(11L, 2L, "stats")));
        when(loadCatalogTopicPort.findAll())
                .thenReturn(List.of(topic(100L, 10L, "machine-learning"), topic(101L, 10L, "deep-learning")));
        when(loadSkeletonPort.findTopicIdsWithSkeleton()).thenReturn(List.of(100L));

        CatalogTreeView tree = service.tree();

        assertThat(tree.domains()).hasSize(2);
        verify(loadCatalogDomainPort, times(1)).findAll();
        verify(loadCatalogFieldPort, times(1)).findAll();
        verify(loadCatalogTopicPort, times(1)).findAll();
    }

    @Test
    @DisplayName("자식이 부모에 정확히 붙는다")
    void treeNestsChildrenUnderParents() {
        when(loadCatalogDomainPort.findAll()).thenReturn(List.of(domain(1L, "cs"), domain(2L, "math")));
        when(loadCatalogFieldPort.findAll()).thenReturn(List.of(field(10L, 1L, "ai"), field(11L, 2L, "stats")));
        when(loadCatalogTopicPort.findAll()).thenReturn(List.of(topic(100L, 10L, "machine-learning")));
        when(loadSkeletonPort.findTopicIdsWithSkeleton()).thenReturn(List.of());

        CatalogTreeView tree = service.tree();

        CatalogTreeView.DomainNode cs = tree.domains().get(0);
        assertThat(cs.slug()).isEqualTo("cs");
        assertThat(cs.fields()).singleElement().satisfies(field -> {
            assertThat(field.slug()).isEqualTo("ai");
            assertThat(field.topics())
                    .extracting(CatalogTreeView.TopicNode::slug)
                    .containsExactly("machine-learning");
        });
        assertThat(tree.domains().get(1).fields().get(0).topics()).isEmpty();
    }

    /** 뼈대가 붙었는지는 신규 생성 비용이 드는지의 표시다. 화면에서 이 차이가 보여야 한다. */
    @Test
    @DisplayName("뼈대 유무가 주제마다 표시된다")
    void treeMarksTopicsWithSkeleton() {
        when(loadCatalogDomainPort.findAll()).thenReturn(List.of(domain(1L, "cs")));
        when(loadCatalogFieldPort.findAll()).thenReturn(List.of(field(10L, 1L, "ai")));
        when(loadCatalogTopicPort.findAll())
                .thenReturn(List.of(topic(100L, 10L, "machine-learning"), topic(101L, 10L, "deep-learning")));
        when(loadSkeletonPort.findTopicIdsWithSkeleton()).thenReturn(List.of(100L));

        List<CatalogTreeView.TopicNode> topics =
                service.tree().domains().get(0).fields().get(0).topics();

        assertThat(topics)
                .extracting(CatalogTreeView.TopicNode::slug, CatalogTreeView.TopicNode::hasSkeleton)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("machine-learning", true),
                        org.assertj.core.groups.Tuple.tuple("deep-learning", false));
    }

    @Test
    @DisplayName("없는 계층 조회는 404 로 이어지는 예외다")
    void missingNodesThrow() {
        when(loadCatalogDomainPort.findById(9L)).thenReturn(Optional.empty());
        when(loadCatalogFieldPort.findById(9L)).thenReturn(Optional.empty());
        when(loadCatalogTopicPort.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.domain(9L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.field(9L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.topic(9L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("계층별 목록 조회는 포트에 그대로 위임한다")
    void delegatesListings() {
        when(loadCatalogDomainPort.findAll()).thenReturn(List.of(domain(1L, "cs")));
        when(loadCatalogFieldPort.findByDomainId(1L)).thenReturn(List.of(field(10L, 1L, "ai")));
        when(loadCatalogTopicPort.findByFieldId(10L)).thenReturn(List.of(topic(100L, 10L, "ml")));
        when(loadTopicAliasPort.findByTopicId(100L)).thenReturn(List.of());

        assertThat(service.domains()).hasSize(1);
        assertThat(service.fields(1L)).hasSize(1);
        assertThat(service.topics(10L)).hasSize(1);
        assertThat(service.aliases(100L)).isEmpty();
    }

    private static CatalogDomain domain(Long id, String slug) {
        return new CatalogDomain(id, slug, "이름-" + slug, null, 0, null, null);
    }

    private static CatalogField field(Long id, Long domainId, String slug) {
        return new CatalogField(id, domainId, slug, "이름-" + slug, null, 0, null, null);
    }

    private static CatalogTopic topic(Long id, Long fieldId, String slug) {
        return new CatalogTopic(id, fieldId, slug, "이름-" + slug, null, 0, null, null);
    }
}
