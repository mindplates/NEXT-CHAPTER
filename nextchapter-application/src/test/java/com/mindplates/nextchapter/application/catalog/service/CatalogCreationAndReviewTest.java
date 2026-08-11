package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogDomainCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogFieldCommand;
import com.mindplates.nextchapter.application.catalog.port.in.command.UpdateCatalogNodeCommand;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.DeleteCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.view.TopicInputLogView;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@DisplayName("카탈로그 생성과 입력 검토")
class CatalogCreationAndReviewTest {

    @Nested
    @DisplayName("도메인·분야 생성")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Creation {

        @Mock
        LoadCatalogDomainPort loadCatalogDomainPort;

        @Mock
        SaveCatalogDomainPort saveCatalogDomainPort;

        @Mock
        DeleteCatalogDomainPort deleteCatalogDomainPort;

        @Mock
        LoadCatalogFieldPort loadCatalogFieldPort;

        @Mock
        SaveCatalogFieldPort saveCatalogFieldPort;

        @Mock
        DeleteCatalogFieldPort deleteCatalogFieldPort;

        @Mock
        LoadCatalogTopicPort loadCatalogTopicPort;

        @InjectMocks
        CatalogDomainService domainService;

        @Test
        @DisplayName("slug 가 중복이면 거절한다")
        void rejectsDuplicateDomainSlug() {
            when(loadCatalogDomainPort.findBySlug("cs")).thenReturn(Optional.of(domain()));

            assertThatThrownBy(() -> domainService.create(new CreateCatalogDomainCommand("cs", "컴퓨터과학", null, 0)))
                    .isInstanceOf(DuplicateException.class);
            verify(saveCatalogDomainPort, never()).save(any());
        }

        @Test
        @DisplayName("중복이 없으면 저장한다")
        void createsDomain() {
            when(loadCatalogDomainPort.findBySlug("cs")).thenReturn(Optional.empty());
            when(saveCatalogDomainPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            CatalogDomain created = domainService.create(new CreateCatalogDomainCommand("CS", "컴퓨터과학", " ", 3));

            assertThat(created.slug()).isEqualTo("cs");
            assertThat(created.description()).isNull();
            assertThat(created.sortOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("없는 도메인 수정은 404 로 이어진다")
        void updateMissingDomain() {
            when(loadCatalogDomainPort.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> domainService.update(9L, new UpdateCatalogNodeCommand("이름", null, 0)))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("도메인 수정은 slug 를 유지한 채 저장된다")
        void updatesDomain() {
            when(loadCatalogDomainPort.findById(1L)).thenReturn(Optional.of(domain()));
            when(saveCatalogDomainPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            CatalogDomain updated = domainService.update(1L, new UpdateCatalogNodeCommand("컴퓨터 과학", "설명", 5));

            assertThat(updated.slug()).isEqualTo("cs");
            assertThat(updated.name()).isEqualTo("컴퓨터 과학");
        }

        @Test
        @DisplayName("분야는 없는 도메인 아래에 만들 수 없다")
        void fieldRequiresExistingDomain() {
            CatalogFieldService fieldService = new CatalogFieldService(
                    loadCatalogFieldPort,
                    saveCatalogFieldPort,
                    deleteCatalogFieldPort,
                    loadCatalogDomainPort,
                    loadCatalogTopicPort);
            when(loadCatalogDomainPort.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fieldService.create(new CreateCatalogFieldCommand(9L, "ai", "인공지능", null, 0)))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("같은 도메인 안에서 slug 가 중복이면 거절한다")
        void rejectsDuplicateFieldSlugInDomain() {
            CatalogFieldService fieldService = new CatalogFieldService(
                    loadCatalogFieldPort,
                    saveCatalogFieldPort,
                    deleteCatalogFieldPort,
                    loadCatalogDomainPort,
                    loadCatalogTopicPort);
            when(loadCatalogDomainPort.findById(1L)).thenReturn(Optional.of(domain()));
            when(loadCatalogFieldPort.findByDomainIdAndSlug(1L, "ai")).thenReturn(Optional.of(field()));

            assertThatThrownBy(() -> fieldService.create(new CreateCatalogFieldCommand(1L, "ai", "인공지능", null, 0)))
                    .isInstanceOf(DuplicateException.class);
        }

        @Test
        @DisplayName("분야 수정은 부모를 유지한다")
        void updatesField() {
            CatalogFieldService fieldService = new CatalogFieldService(
                    loadCatalogFieldPort,
                    saveCatalogFieldPort,
                    deleteCatalogFieldPort,
                    loadCatalogDomainPort,
                    loadCatalogTopicPort);
            when(loadCatalogFieldPort.findById(10L)).thenReturn(Optional.of(field()));
            when(saveCatalogFieldPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            CatalogField updated = fieldService.update(10L, new UpdateCatalogNodeCommand("AI", null, 1));

            assertThat(updated.domainId()).isEqualTo(1L);
            assertThat(updated.name()).isEqualTo("AI");
        }

        private CatalogDomain domain() {
            return new CatalogDomain(1L, "cs", "컴퓨터과학", null, 0, null, null);
        }

        private CatalogField field() {
            return new CatalogField(10L, 1L, "ai", "인공지능", null, 0, null, null);
        }
    }

    @Nested
    @DisplayName("입력 검토")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Review {

        @Mock
        LoadTopicInputLogPort loadTopicInputLogPort;

        @Mock
        SaveTopicInputLogPort saveTopicInputLogPort;

        @InjectMocks
        TopicInputReviewService service;

        /** 반복 횟수가 검토 우선순위다. 행마다 붙지 않으면 무엇을 먼저 볼지 알 수 없다. */
        @Test
        @DisplayName("행마다 같은 표현의 반복 횟수가 붙는다")
        void listAttachesRepeatCount() {
            TopicInputLog log = TopicInputLog.needsReview("바이올린", null, null, "user-1");
            when(loadTopicInputLogPort.search(
                            any(),
                            org.mockito.ArgumentMatchers.anyBoolean(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(new PagedResult<>(List.of(log), 1));
            when(loadTopicInputLogPort.countByNormalized(anyString())).thenReturn(4L);

            PagedResult<TopicInputLogView> result =
                    service.list(List.of(TopicInputResolution.NEEDS_REVIEW), true, 0, 20);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).singleElement().satisfies(view -> {
                assertThat(view.rawInput()).isEqualTo("바이올린");
                assertThat(view.sameInputCount()).isEqualTo(4);
                assertThat(view.reviewed()).isFalse();
            });
        }

        @Test
        @DisplayName("검토 표시는 검토자와 메모를 남긴다")
        void marksReviewed() {
            when(loadTopicInputLogPort.findById(5L))
                    .thenReturn(Optional.of(TopicInputLog.needsReview("바이올린", null, null, "user-1")));
            when(saveTopicInputLogPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(loadTopicInputLogPort.countByNormalized(anyString())).thenReturn(1L);

            TopicInputLogView view = service.markReviewed(5L, "admin@nextchapter.local", "기각");

            assertThat(view.reviewed()).isTrue();
            assertThat(view.reviewedBy()).isEqualTo("admin@nextchapter.local");
            assertThat(view.reviewNote()).isEqualTo("기각");
        }

        @Test
        @DisplayName("없는 로그는 404 로 이어진다")
        void missingLog() {
            when(loadTopicInputLogPort.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markReviewed(9L, "admin", null))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
