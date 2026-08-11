package com.mindplates.nextchapter.application.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.EmbedTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SearchSimilarTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * M1 의 완료 기준을 직접 겨냥한 테스트.
 *
 * <p>"머신러닝" · "기계학습" · "ML 입문" · "머신러닝 배우고 싶어"가 같은 주제 ID 로 수렴하는지를 네
 * 경로(주제명 일치 · 별칭 일치 · LLM 매핑)로 나눠 확인한다. 이 수렴이 깨지면 집단 루프의 전제가
 * 무너지므로, 구현 세부가 아니라 <b>결과가 하나로 모이는지</b>를 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("주제 입력 해소")
class TopicResolutionServiceTest {

    private static final Long TOPIC_ID = 30L;
    private static final Long FIELD_ID = 20L;
    private static final Long DOMAIN_ID = 10L;

    @Mock
    LoadCatalogDomainPort loadCatalogDomainPort;

    @Mock
    LoadCatalogFieldPort loadCatalogFieldPort;

    @Mock
    LoadCatalogTopicPort loadCatalogTopicPort;

    @Mock
    LoadTopicAliasPort loadTopicAliasPort;

    @Mock
    LoadTopicInputLogPort loadTopicInputLogPort;

    @Mock
    SaveTopicInputLogPort saveTopicInputLogPort;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    CreateCatalogTopicUseCase createCatalogTopicUseCase;

    @Mock
    ManageTopicAliasUseCase manageTopicAliasUseCase;

    @Mock
    SearchSimilarTopicsUseCase searchSimilarTopicsUseCase;

    @Mock
    EmbedTopicUseCase embedTopicUseCase;

    @Mock
    AiGateway aiGateway;

    @InjectMocks
    TopicResolutionService service;

    @BeforeEach
    void setUpCatalog() {
        when(loadCatalogDomainPort.findAll()).thenReturn(List.of(domain()));
        when(loadCatalogDomainPort.findById(DOMAIN_ID)).thenReturn(Optional.of(domain()));
        when(loadCatalogFieldPort.findByDomainId(DOMAIN_ID)).thenReturn(List.of(field()));
        when(loadCatalogFieldPort.findById(FIELD_ID)).thenReturn(Optional.of(field()));
        when(loadCatalogTopicPort.findAll()).thenReturn(List.of(topic()));
        when(loadCatalogTopicPort.findByFieldId(FIELD_ID)).thenReturn(List.of(topic()));
        when(loadCatalogTopicPort.findById(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(loadTopicAliasPort.findByNormalized(anyString())).thenReturn(Optional.empty());
        when(loadSkeletonPort.findByTopicId(any())).thenReturn(Optional.empty());
        when(saveTopicInputLogPort.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
    }

    @Test
    @DisplayName("주제명과 같은 입력은 모델을 부르지 않고 수렴한다")
    void exactNameMatchSkipsLlm() {
        TopicResolutionView view = service.resolve("머신러닝", "user-1");

        assertThat(view.topicId()).isEqualTo(TOPIC_ID);
        assertThat(view.matchSource()).isEqualTo(TopicMatchSource.ALIAS);
        verifyNoInteractions(aiGateway);
    }

    @Test
    @DisplayName("표기가 달라도 주제명과 같으면 수렴한다 — 정규화가 공백·구두점을 접는다")
    void normalizedNameMatch() {
        assertThat(service.resolve("머신 러닝!", "user-1").topicId()).isEqualTo(TOPIC_ID);
        verifyNoInteractions(aiGateway);
    }

    @Test
    @DisplayName("별칭에 등록된 표현도 모델 없이 수렴한다")
    void aliasMatchSkipsLlm() {
        when(loadTopicAliasPort.findByNormalized(anyString()))
                .thenReturn(Optional.of(new TopicAlias(9L, TOPIC_ID, "기계학습", null)));

        TopicResolutionView view = service.resolve("기계학습", "user-1");

        assertThat(view.topicId()).isEqualTo(TOPIC_ID);
        assertThat(view.matchSource()).isEqualTo(TopicMatchSource.ALIAS);
        verifyNoInteractions(aiGateway);
    }

    @Test
    @DisplayName("문장형 입력은 LLM 단계적 매핑으로 수렴한다")
    void sentenceInputResolvesViaLlm() {
        stubNarrowing("computer-science", "ai", "machine-learning");

        TopicResolutionView view = service.resolve("머신러닝 배우고 싶어", "user-1");

        assertThat(view.topicId()).isEqualTo(TOPIC_ID);
        assertThat(view.matchSource()).isEqualTo(TopicMatchSource.LLM);
        assertThat(view.fieldName()).isEqualTo("인공지능");
        assertThat(view.domainName()).isEqualTo("컴퓨터과학");
    }

    /** M1 완료 기준 1번을 그대로 옮긴 테스트. */
    @Test
    @DisplayName("네 가지 표현이 모두 같은 주제 ID 로 수렴한다")
    void allExpressionsConvergeToSameTopic() {
        when(loadTopicAliasPort.findByNormalized(anyString())).thenAnswer(invocation -> {
            String normalized = invocation.getArgument(0);
            boolean registered =
                    normalized.equals(com.mindplates.nextchapter.common.text.Strings.normalizeForMatch("ML 입문"));
            return registered ? Optional.of(new TopicAlias(9L, TOPIC_ID, "ML 입문", null)) : Optional.empty();
        });
        stubNarrowing("computer-science", "ai", "machine-learning");

        List<Long> resolved = List.of("머신러닝", "기계 학습", "ML 입문", "머신러닝 배우고 싶어").stream()
                .map(input -> service.resolve(input, "user-1").topicId())
                .toList();

        assertThat(resolved).containsExactly(TOPIC_ID, TOPIC_ID, TOPIC_ID, TOPIC_ID);
    }

    @Test
    @DisplayName("주제까지 좁혀지지 않으면 후보를 제시하고, 붙일 분야가 있으므로 신규 생성이 가능하다")
    void presentsCandidatesWhenTopicUnmatched() {
        stubNarrowing("computer-science", "ai", null);
        when(searchSimilarTopicsUseCase.search(anyString(), anyInt())).thenReturn(List.of(similarity()));

        TopicResolutionView view = service.resolve("강화학습 알려줘", "user-1");

        assertThat(view.resolution()).isEqualTo(TopicInputResolution.CANDIDATES_PRESENTED);
        assertThat(view.candidates()).hasSize(1);
        assertThat(view.canCreateNew()).isTrue();
        assertThat(view.proposedTopicName()).isEqualTo("강화학습");
        assertThat(view.requestId()).isEqualTo(1L);
        verify(createCatalogTopicUseCase, never()).create(any());
    }

    @Test
    @DisplayName("도메인부터 못 좁히면 신규 생성이 불가하다 — 신규 도메인·분야는 자동으로 만들지 않는다")
    void cannotCreateWhenNoFieldChosen() {
        when(aiGateway.complete(any(), anyString(), anyString(), anyInt())).thenReturn(completion("{\"slug\": null}"));
        when(searchSimilarTopicsUseCase.search(anyString(), anyInt())).thenReturn(List.of(similarity()));

        TopicResolutionView view = service.resolve("바이올린 배우기", "user-1");

        assertThat(view.resolution()).isEqualTo(TopicInputResolution.CANDIDATES_PRESENTED);
        assertThat(view.canCreateNew()).isFalse();
    }

    @Test
    @DisplayName("후보도 없고 붙일 자리도 없으면 바로 검토로 간다 — 사용자가 할 수 있는 일이 없다")
    void escalatesWhenNothingToOffer() {
        when(aiGateway.complete(any(), anyString(), anyString(), anyInt())).thenReturn(completion("{\"slug\": null}"));
        when(searchSimilarTopicsUseCase.search(anyString(), anyInt())).thenReturn(List.of());

        TopicResolutionView view = service.resolve("바이올린 배우기", "user-1");

        assertThat(view.resolution()).isEqualTo(TopicInputResolution.NEEDS_REVIEW);
        assertThat(view.canCreateNew()).isFalse();
    }

    @Test
    @DisplayName("LLM 이 죽어도 요청이 실패하지 않는다 — 후보 제시 경로로 흘러간다")
    void llmFailureDoesNotFailRequest() {
        when(aiGateway.complete(any(), anyString(), anyString(), anyInt()))
                .thenThrow(new ExternalApiException("벤더 오류"));
        when(searchSimilarTopicsUseCase.search(anyString(), anyInt())).thenReturn(List.of(similarity()));

        TopicResolutionView view = service.resolve("무언가 배우고 싶어", "user-1");

        assertThat(view.resolution()).isEqualTo(TopicInputResolution.CANDIDATES_PRESENTED);
    }

    @Test
    @DisplayName("후보를 고르면 그 표현이 별칭으로 승격된다 — 다음 번 같은 입력은 모델을 부르지 않는다")
    void confirmPromotesInputToAlias() {
        when(loadTopicInputLogPort.findById(1L)).thenReturn(Optional.of(pending(FIELD_ID)));

        TopicResolutionView view = service.confirmCandidate(1L, TOPIC_ID);

        assertThat(view.matchSource()).isEqualTo(TopicMatchSource.USER_CONFIRMED);
        verify(manageTopicAliasUseCase).add(TOPIC_ID, "강화학습 알려줘");
    }

    @Test
    @DisplayName("제시하지 않은 주제는 고를 수 없다")
    void rejectsUnofferedTopic() {
        when(loadTopicInputLogPort.findById(1L)).thenReturn(Optional.of(pending(FIELD_ID)));

        assertThatThrownBy(() -> service.confirmCandidate(1L, 999L)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("같은 요청 토큰을 두 번 쓸 수 없다")
    void tokenIsSingleUse() {
        when(loadTopicInputLogPort.findById(1L))
                .thenReturn(Optional.of(TopicInputLog.matched("머신러닝", TOPIC_ID, TopicMatchSource.ALIAS, "user-1")));

        assertThatThrownBy(() -> service.confirmCandidate(1L, TOPIC_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("이미 처리된");
    }

    @Test
    @DisplayName("전부 거절하면 LLM 이 제안한 slug 로 신규 주제를 만들고 즉시 임베딩한다")
    void rejectCreatesNewTopicWithProposedSlug() {
        when(loadTopicInputLogPort.findById(1L)).thenReturn(Optional.of(pending(FIELD_ID)));
        when(loadCatalogTopicPort.findBySlug("reinforcement-learning")).thenReturn(Optional.empty());
        CatalogTopic created = new CatalogTopic(31L, FIELD_ID, "reinforcement-learning", "강화학습", null, 0, null, null);
        when(createCatalogTopicUseCase.create(any())).thenReturn(created);

        TopicResolutionView view = service.rejectCandidates(1L);

        ArgumentCaptor<CreateCatalogTopicCommand> command = ArgumentCaptor.forClass(CreateCatalogTopicCommand.class);
        verify(createCatalogTopicUseCase).create(command.capture());
        assertThat(command.getValue().slug()).isEqualTo("reinforcement-learning");
        assertThat(command.getValue().name()).isEqualTo("강화학습");
        assertThat(command.getValue().fieldId()).isEqualTo(FIELD_ID);
        assertThat(view.resolution()).isEqualTo(TopicInputResolution.NEW_TOPIC_CREATED);
        verify(embedTopicUseCase).embed(31L);
        verify(manageTopicAliasUseCase).add(31L, "강화학습 알려줘");
    }

    @Test
    @DisplayName("slug 가 이미 있으면 접미사를 붙인다")
    void suffixesConflictingSlug() {
        when(loadTopicInputLogPort.findById(1L)).thenReturn(Optional.of(pending(FIELD_ID)));
        when(loadCatalogTopicPort.findBySlug("reinforcement-learning")).thenReturn(Optional.of(topic()));
        when(loadCatalogTopicPort.findBySlug("reinforcement-learning-2")).thenReturn(Optional.empty());
        when(createCatalogTopicUseCase.create(any()))
                .thenReturn(new CatalogTopic(31L, FIELD_ID, "reinforcement-learning-2", "강화학습", null, 0, null, null));

        service.rejectCandidates(1L);

        ArgumentCaptor<CreateCatalogTopicCommand> command = ArgumentCaptor.forClass(CreateCatalogTopicCommand.class);
        verify(createCatalogTopicUseCase).create(command.capture());
        assertThat(command.getValue().slug()).isEqualTo("reinforcement-learning-2");
    }

    @Test
    @DisplayName("붙일 분야가 없으면 신규 주제를 만들지 않고 검토로 넘긴다")
    void rejectWithoutFieldEscalates() {
        when(loadTopicInputLogPort.findById(1L)).thenReturn(Optional.of(pending(null)));

        TopicResolutionView view = service.rejectCandidates(1L);

        assertThat(view.resolution()).isEqualTo(TopicInputResolution.NEEDS_REVIEW);
        verify(createCatalogTopicUseCase, never()).create(any());
    }

    // --- 픽스처 -------------------------------------------------------------

    /**
     * 프롬프트 내용을 보고 답한다. 호출 순서에 고정된 스텁을 쓰면 입력이 여러 개인 테스트에서
     * 스텁이 소진돼 두 번째 입력이 엉뚱한 응답을 받는다 — 그건 구현이 아니라 테스트의 결함이다.
     */
    private void stubNarrowing(String domainSlug, String fieldSlug, String topicSlug) {
        String topicResponse = topicSlug == null
                ? "{\"slug\": null, \"proposedName\": \"강화학습\", \"proposedSlug\": \"reinforcement-learning\"}"
                : "{\"slug\": \"" + topicSlug + "\"}";
        when(aiGateway.complete(any(), anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(2);
            if (prompt.contains("아래 도메인 중")) {
                return completion("{\"slug\": \"" + domainSlug + "\"}");
            }
            if (prompt.contains("아래 분야 중")) {
                return completion("{\"slug\": \"" + fieldSlug + "\"}");
            }
            return completion(topicResponse);
        });
    }

    private static LlmCompletionResult completion(String text) {
        return new LlmCompletionResult(text, 100, 20);
    }

    private static CatalogDomain domain() {
        return new CatalogDomain(DOMAIN_ID, "computer-science", "컴퓨터과학", null, 0, null, null);
    }

    private static CatalogField field() {
        return new CatalogField(FIELD_ID, DOMAIN_ID, "ai", "인공지능", null, 0, null, null);
    }

    private static CatalogTopic topic() {
        return new CatalogTopic(TOPIC_ID, FIELD_ID, "machine-learning", "머신러닝", null, 0, null, null);
    }

    private static TopicSimilarityView similarity() {
        return new TopicSimilarityView(TOPIC_ID, "machine-learning", "머신러닝", FIELD_ID, "인공지능", "컴퓨터과학", 0.71);
    }

    private static TopicInputLog pending(Long proposedFieldId) {
        return withId(
                TopicInputLog.candidatesPresented(
                        "강화학습 알려줘", List.of(TOPIC_ID), proposedFieldId, "강화학습", "reinforcement-learning", "user-1"),
                1L);
    }

    private static TopicInputLog withId(TopicInputLog log, Long id) {
        return new TopicInputLog(
                log.id() == null ? id : log.id(),
                log.rawInput(),
                log.normalized(),
                log.resolution(),
                log.matchSource(),
                log.matchedTopicId(),
                log.proposedFieldId(),
                log.proposedTopicName(),
                log.proposedTopicSlug(),
                log.candidateTopicIds(),
                log.userId(),
                log.reviewedAt(),
                log.reviewedBy(),
                log.reviewNote(),
                log.createdAt(),
                log.updatedAt());
    }
}
