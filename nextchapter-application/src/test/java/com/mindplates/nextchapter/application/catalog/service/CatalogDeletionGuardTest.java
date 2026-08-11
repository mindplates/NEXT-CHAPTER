package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogTopicPort;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 삭제 가드는 이 계층의 유일한 실질 로직이라 목킹 단위 테스트로 덮는다.
 *
 * <p>가드가 없으면 FK 위반이 드라이버 예외로 500 이 되어 운영자가 원인을 알 수 없고, 주제 삭제는
 * 그보다 나쁘다 — 뼈대에 붙은 집단 신호가 함께 사라진다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("카탈로그 삭제 가드")
class CatalogDeletionGuardTest {

    @Nested
    @DisplayName("도메인 삭제")
    @ExtendWith(MockitoExtension.class)
    class DomainDeletion {

        @Mock
        LoadCatalogDomainPort loadCatalogDomainPort;

        @Mock
        SaveCatalogDomainPort saveCatalogDomainPort;

        @Mock
        DeleteCatalogDomainPort deleteCatalogDomainPort;

        @Mock
        LoadCatalogFieldPort loadCatalogFieldPort;

        @InjectMocks
        CatalogDomainService service;

        @Test
        @DisplayName("분야가 남아 있으면 거절하고 개수를 알려준다")
        void rejectsWhenFieldsRemain() {
            when(loadCatalogDomainPort.findById(1L)).thenReturn(Optional.of(domain(1L)));
            when(loadCatalogFieldPort.countByDomainId(1L)).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("3개");

            verify(deleteCatalogDomainPort, never()).deleteById(any());
        }

        @Test
        @DisplayName("자식이 없으면 삭제한다")
        void deletesWhenEmpty() {
            when(loadCatalogDomainPort.findById(1L)).thenReturn(Optional.of(domain(1L)));
            when(loadCatalogFieldPort.countByDomainId(1L)).thenReturn(0L);

            service.delete(1L);

            verify(deleteCatalogDomainPort).deleteById(1L);
        }

        @Test
        @DisplayName("없는 도메인은 404 로 이어지는 예외다")
        void missingDomain() {
            when(loadCatalogDomainPort.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("분야 삭제")
    @ExtendWith(MockitoExtension.class)
    class FieldDeletion {

        @Mock
        LoadCatalogFieldPort loadCatalogFieldPort;

        @Mock
        SaveCatalogFieldPort saveCatalogFieldPort;

        @Mock
        DeleteCatalogFieldPort deleteCatalogFieldPort;

        @Mock
        LoadCatalogDomainPort loadCatalogDomainPort;

        @Mock
        LoadCatalogTopicPort loadCatalogTopicPort;

        @InjectMocks
        CatalogFieldService service;

        @Test
        @DisplayName("주제가 남아 있으면 거절한다")
        void rejectsWhenTopicsRemain() {
            when(loadCatalogFieldPort.findById(2L)).thenReturn(Optional.of(field(2L)));
            when(loadCatalogTopicPort.countByFieldId(2L)).thenReturn(1L);

            assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(InvalidOperationException.class);

            verify(deleteCatalogFieldPort, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("주제 삭제")
    @ExtendWith(MockitoExtension.class)
    class TopicDeletion {

        @Mock
        LoadCatalogTopicPort loadCatalogTopicPort;

        @Mock
        SaveCatalogTopicPort saveCatalogTopicPort;

        @Mock
        DeleteCatalogTopicPort deleteCatalogTopicPort;

        @Mock
        LoadCatalogFieldPort loadCatalogFieldPort;

        @Mock
        LoadSkeletonPort loadSkeletonPort;

        @InjectMocks
        CatalogTopicService service;

        @Test
        @DisplayName("뼈대가 붙어 있으면 거절한다 — 집단 신호의 근거가 딸려 있다")
        void rejectsWhenSkeletonAttached() {
            when(loadCatalogTopicPort.findById(3L)).thenReturn(Optional.of(topic(3L)));
            when(loadSkeletonPort.findByTopicId(3L)).thenReturn(Optional.of(Skeleton.start(3L)));

            assertThatThrownBy(() -> service.delete(3L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("뼈대");

            verify(deleteCatalogTopicPort, never()).deleteById(any());
        }

        @Test
        @DisplayName("뼈대가 없으면 삭제한다")
        void deletesWhenNoSkeleton() {
            when(loadCatalogTopicPort.findById(3L)).thenReturn(Optional.of(topic(3L)));
            when(loadSkeletonPort.findByTopicId(3L)).thenReturn(Optional.empty());

            service.delete(3L);

            verify(deleteCatalogTopicPort).deleteById(3L);
        }

        @Test
        @DisplayName("수정은 slug 를 유지한 채 저장한다")
        void updateKeepsSlug() {
            when(loadCatalogTopicPort.findById(3L)).thenReturn(Optional.of(topic(3L)));
            when(saveCatalogTopicPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            CatalogTopic updated = service.update(
                    3L,
                    new com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand(
                            "새 이름", "새 설명", 2));

            assertThat(updated.slug()).isEqualTo("machine-learning");
            assertThat(updated.name()).isEqualTo("새 이름");
        }
    }

    private static CatalogDomain domain(Long id) {
        return new CatalogDomain(id, "cs", "컴퓨터과학", null, 0, null, null);
    }

    private static CatalogField field(Long id) {
        return new CatalogField(id, 1L, "ai", "인공지능", null, 0, null, null);
    }

    private static CatalogTopic topic(Long id) {
        return new CatalogTopic(id, 2L, "machine-learning", "머신러닝", null, 0, null, null);
    }
}
