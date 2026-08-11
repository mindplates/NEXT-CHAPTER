package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.out.DeleteTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicAliasPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("주제 별칭 등록")
class TopicAliasServiceTest {

    @Mock
    LoadTopicAliasPort loadTopicAliasPort;

    @Mock
    SaveTopicAliasPort saveTopicAliasPort;

    @Mock
    DeleteTopicAliasPort deleteTopicAliasPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @InjectMocks
    TopicAliasService service;

    @Test
    @DisplayName("표기만 다른 별칭이 이미 있으면 거절한다 — 한 표현이 두 주제를 가리킬 수 없다")
    void rejectsDuplicateAcrossSpacing() {
        when(loadCatalogTopicPort.findById(1L)).thenReturn(Optional.of(topic(1L)));
        when(loadTopicAliasPort.findByNormalized(any())).thenReturn(Optional.of(new TopicAlias(9L, 2L, "기계학습", null)));

        assertThatThrownBy(() -> service.add(1L, "기계 학습"))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("2");

        verify(saveTopicAliasPort, never()).save(any());
    }

    @Test
    @DisplayName("중복이 없으면 등록한다")
    void addsWhenUnique() {
        when(loadCatalogTopicPort.findById(1L)).thenReturn(Optional.of(topic(1L)));
        when(loadTopicAliasPort.findByNormalized(any())).thenReturn(Optional.empty());
        when(saveTopicAliasPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TopicAlias saved = service.add(1L, "ML 입문");

        assertThat(saved.topicId()).isEqualTo(1L);
        assertThat(saved.alias()).isEqualTo("ML 입문");
    }

    @Test
    @DisplayName("없는 주제에는 별칭을 달 수 없다")
    void rejectsMissingTopic() {
        when(loadCatalogTopicPort.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(1L, "머신러닝")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("없는 별칭은 삭제할 수 없다")
    void rejectsMissingAliasOnRemove() {
        when(loadTopicAliasPort.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(5L)).isInstanceOf(EntityNotFoundException.class);
        verify(deleteTopicAliasPort, never()).deleteById(any());
    }

    private static CatalogTopic topic(Long id) {
        return new CatalogTopic(id, 2L, "machine-learning", "머신러닝", null, 0, null, null);
    }
}
