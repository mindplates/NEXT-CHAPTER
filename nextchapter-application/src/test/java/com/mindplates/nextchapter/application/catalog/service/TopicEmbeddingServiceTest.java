package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.view.EmbeddingStatusView;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("주제 임베딩 서비스")
class TopicEmbeddingServiceTest {

    private static final String MODEL = "voyage-3-large";

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadTopicAliasPort loadTopicAliasPort;

    @Mock
    LoadTopicEmbeddingPort loadTopicEmbeddingPort;

    @Mock
    SaveTopicEmbeddingPort saveTopicEmbeddingPort;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    TopicEmbeddingService service;

    @Test
    @DisplayName("이름 · 별칭 · 설명을 합쳐 임베딩한다 — 이름만으로는 표기 거리 비교에 그친다")
    void embedsNameAliasesAndDescription() {
        when(loadCatalogTopicPort.findById(1L)).thenReturn(Optional.of(topic()));
        when(loadTopicAliasPort.findByTopicId(1L))
                .thenReturn(List.of(TopicAlias.create(1L, "기계학습"), TopicAlias.create(1L, "ML 입문")));
        when(aiGateway.embed(any(), any())).thenReturn(embeddingResult());
        when(saveTopicEmbeddingPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.embed(1L);

        ArgumentCaptor<List<String>> inputs = ArgumentCaptor.forClass(List.class);
        verify(aiGateway).embed(any(), inputs.capture());
        assertThat(inputs.getValue()).singleElement().satisfies(text -> assertThat(text)
                .contains("머신러닝")
                .contains("기계학습")
                .contains("ML 입문")
                .contains("경사하강법부터"));
    }

    @Test
    @DisplayName("저장되는 모델은 게이트웨이가 돌려준 실제 모델이다 — 서비스가 모델을 정하지 않는다")
    void storesModelFromResult() {
        when(loadCatalogTopicPort.findById(1L)).thenReturn(Optional.of(topic()));
        when(loadTopicAliasPort.findByTopicId(1L)).thenReturn(List.of());
        when(aiGateway.embed(any(), any())).thenReturn(embeddingResult());
        when(saveTopicEmbeddingPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TopicEmbedding saved = service.embed(1L);

        assertThat(saved.model()).isEqualTo(MODEL);
        assertThat(saved.dimension()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색은 현재 모델로 걸러 조회한다")
    void searchFiltersByCurrentModel() {
        when(aiGateway.currentEmbeddingModel()).thenReturn(MODEL);
        when(aiGateway.embed(any(), any())).thenReturn(embeddingResult());
        when(loadTopicEmbeddingPort.searchSimilar(any(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.search("머신러닝 배우고 싶어", 3);

        verify(loadTopicEmbeddingPort).searchSimilar(any(), eq(MODEL), eq(3), anyDouble());
    }

    @Test
    @DisplayName("limit 이 0 이하면 기본값을 쓴다")
    void searchFallsBackToDefaultLimit() {
        when(aiGateway.currentEmbeddingModel()).thenReturn(MODEL);
        when(aiGateway.embed(any(), any())).thenReturn(embeddingResult());
        when(loadTopicEmbeddingPort.searchSimilar(any(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.search("머신러닝", 0);

        verify(loadTopicEmbeddingPort).searchSimilar(any(), eq(MODEL), eq(5), anyDouble());
    }

    @Test
    @DisplayName("한 주제가 실패해도 배치가 멈추지 않는다 — 남은 것은 다음 호출이 다시 집는다")
    void batchIsolatesFailures() {
        when(aiGateway.currentEmbeddingModel()).thenReturn(MODEL);
        when(loadTopicEmbeddingPort.findTopicIdsNotEmbeddedWith(MODEL, 3)).thenReturn(List.of(1L, 2L, 3L));
        when(loadCatalogTopicPort.findById(any())).thenReturn(Optional.of(topic()));
        when(loadTopicAliasPort.findByTopicId(any())).thenReturn(List.of());
        when(aiGateway.embed(any(), any()))
                .thenReturn(embeddingResult())
                .thenThrow(new ExternalApiException("벤더 오류"))
                .thenReturn(embeddingResult());
        when(saveTopicEmbeddingPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loadTopicEmbeddingPort.countByModel(MODEL)).thenReturn(2L);
        when(loadTopicEmbeddingPort.countTopicsNotEmbeddedWith(MODEL)).thenReturn(1L);

        EmbeddingStatusView status = service.reembedBatch(3);

        verify(saveTopicEmbeddingPort, times(2)).save(any());
        assertThat(status.embedded()).isEqualTo(2);
        assertThat(status.outdated()).isEqualTo(1);
        assertThat(status.isUpToDate()).isFalse();
    }

    @Test
    @DisplayName("남은 대상이 0 이면 최신 상태다")
    void statusReportsUpToDate() {
        when(aiGateway.currentEmbeddingModel()).thenReturn(MODEL);
        when(loadTopicEmbeddingPort.countByModel(MODEL)).thenReturn(30L);
        when(loadTopicEmbeddingPort.countTopicsNotEmbeddedWith(MODEL)).thenReturn(0L);

        assertThat(service.status().isUpToDate()).isTrue();
    }

    private static CatalogTopic topic() {
        return new CatalogTopic(1L, 2L, "machine-learning", "머신러닝", "경사하강법부터 시작한다", 0, null, null);
    }

    private static EmbeddingResult embeddingResult() {
        return new EmbeddingResult(MODEL, 3, List.of(new float[] {0.1f, 0.2f, 0.3f}), 8);
    }
}
