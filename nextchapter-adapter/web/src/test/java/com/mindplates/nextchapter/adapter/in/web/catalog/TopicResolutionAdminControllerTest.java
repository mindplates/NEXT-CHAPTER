package com.mindplates.nextchapter.adapter.in.web.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.catalog.port.in.EmbedTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ReembedTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ResolveTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ReviewTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SearchSimilarTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.view.EmbeddingStatusView;
import com.mindplates.nextchapter.application.catalog.view.TopicInputLogView;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    TopicResolutionAdminController.class,
    TopicInputLogAdminController.class,
    TopicEmbeddingAdminController.class
})
@Import(GlobalExceptionHandler.class)
@DisplayName("주제 해소·검토·임베딩 API")
class TopicResolutionAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ResolveTopicInputUseCase resolveTopicInputUseCase;

    @MockitoBean
    ReviewTopicInputUseCase reviewTopicInputUseCase;

    @MockitoBean
    ReembedTopicsUseCase reembedTopicsUseCase;

    @MockitoBean
    EmbedTopicUseCase embedTopicUseCase;

    @MockitoBean
    SearchSimilarTopicsUseCase searchSimilarTopicsUseCase;

    @Test
    @DisplayName("매칭 결과는 주제와 계층 이름을 함께 준다")
    void resolveReturnsMatch() throws Exception {
        when(resolveTopicInputUseCase.resolve(eq("머신러닝"), any())).thenReturn(matched());

        mockMvc.perform(post("/api/admin/catalog/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"머신러닝\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolution").value("MATCHED"))
                .andExpect(jsonPath("$.data.topicId").value(30))
                .andExpect(jsonPath("$.data.fieldName").value("인공지능"));
    }

    @Test
    @DisplayName("빈 입력은 400 이다")
    void rejectsBlankInput() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("input"));
    }

    @Test
    @DisplayName("후보 제시 결과에 요청 ID 와 신규 생성 가능 여부가 실린다")
    void resolveReturnsCandidates() throws Exception {
        when(resolveTopicInputUseCase.resolve(anyString(), any()))
                .thenReturn(new TopicResolutionView(
                        7L,
                        TopicInputResolution.CANDIDATES_PRESENTED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(new TopicSimilarityView(30L, "machine-learning", "머신러닝", 20L, "인공지능", "컴퓨터과학", 0.7)),
                        true,
                        "강화학습"));

        mockMvc.perform(post("/api/admin/catalog/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"강화학습 알려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(7))
                .andExpect(jsonPath("$.data.canCreateNew").value(true))
                .andExpect(jsonPath("$.data.candidates[0].score").value(0.7));
    }

    @Test
    @DisplayName("확인과 거절이 요청 ID 로 연결된다")
    void confirmAndReject() throws Exception {
        when(resolveTopicInputUseCase.confirmCandidate(7L, 30L)).thenReturn(matched());
        when(resolveTopicInputUseCase.rejectCandidates(7L)).thenReturn(matched());

        mockMvc.perform(post("/api/admin/catalog/resolve/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":30}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/catalog/resolve/7/reject")).andExpect(status().isOk());

        verify(resolveTopicInputUseCase).confirmCandidate(7L, 30L);
        verify(resolveTopicInputUseCase).rejectCandidates(7L);
    }

    @Test
    @DisplayName("이미 처리된 요청은 400 이다")
    void alreadyResolvedIsBadRequest() throws Exception {
        when(resolveTopicInputUseCase.rejectCandidates(7L)).thenThrow(new InvalidOperationException("이미 처리된 요청입니다."));

        mockMvc.perform(post("/api/admin/catalog/resolve/7/reject")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검토 대기열의 기본 필터는 미검토 + NEEDS_REVIEW 다")
    void reviewQueueDefaultsToNeedsReview() throws Exception {
        when(reviewTopicInputUseCase.list(any(), eq(true), eq(0), eq(20)))
                .thenReturn(new PagedResult<>(List.of(logView()), 1));

        mockMvc.perform(get("/api/admin/catalog/input-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].sameInputCount").value(3));

        verify(reviewTopicInputUseCase).list(List.of(TopicInputResolution.NEEDS_REVIEW), true, 0, 20);
    }

    @Test
    @DisplayName("전체 기록 조회는 필터 없이 본다 — 매칭 품질 확인 경로")
    void listAllUsesNoFilter() throws Exception {
        when(reviewTopicInputUseCase.list(any(), eq(false), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(), 0));

        mockMvc.perform(get("/api/admin/catalog/input-logs/all")).andExpect(status().isOk());

        verify(reviewTopicInputUseCase).list(List.of(), false, 0, 20);
    }

    @Test
    @DisplayName("페이지 크기는 상한으로 잘린다")
    void clampsPageSize() throws Exception {
        when(reviewTopicInputUseCase.list(any(), eq(true), anyInt(), anyInt()))
                .thenReturn(new PagedResult<>(List.of(), 0));

        mockMvc.perform(get("/api/admin/catalog/input-logs?size=5000")).andExpect(status().isOk());

        verify(reviewTopicInputUseCase).list(List.of(TopicInputResolution.NEEDS_REVIEW), true, 0, 100);
    }

    @Test
    @DisplayName("검토 표시는 본문 없이도 된다")
    void reviewWithoutBody() throws Exception {
        when(reviewTopicInputUseCase.markReviewed(eq(5L), any(), any())).thenReturn(logView());

        mockMvc.perform(post("/api/admin/catalog/input-logs/5/review")).andExpect(status().isOk());

        verify(reviewTopicInputUseCase).markReviewed(5L, null, null);
    }

    @Test
    @DisplayName("임베딩 현황과 배치가 연결돼 있고, 배치 크기가 상한으로 잘린다")
    void embeddingEndpoints() throws Exception {
        when(reembedTopicsUseCase.status()).thenReturn(new EmbeddingStatusView("voyage-3-large", 30, 2));
        when(reembedTopicsUseCase.reembedBatch(anyInt())).thenReturn(new EmbeddingStatusView("voyage-3-large", 32, 0));

        mockMvc.perform(get("/api/admin/catalog/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outdated").value(2))
                .andExpect(jsonPath("$.data.upToDate").value(false));

        mockMvc.perform(post("/api/admin/catalog/embeddings/reembed?batchSize=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upToDate").value(true));

        verify(reembedTopicsUseCase).reembedBatch(100);
    }

    @Test
    @DisplayName("주제 하나만 다시 임베딩할 수 있다")
    void embedsSingleTopic() throws Exception {
        when(reembedTopicsUseCase.status()).thenReturn(new EmbeddingStatusView("voyage-3-large", 31, 1));

        mockMvc.perform(post("/api/admin/catalog/embeddings/topics/30")).andExpect(status().isOk());

        verify(embedTopicUseCase).embed(30L);
    }

    @Test
    @DisplayName("운영자가 컷오프·후보 품질을 눈으로 확인하는 검색 경로가 있다")
    void searchEndpoint() throws Exception {
        when(searchSimilarTopicsUseCase.search("머신러닝", 3))
                .thenReturn(
                        List.of(new TopicSimilarityView(30L, "machine-learning", "머신러닝", 20L, "인공지능", "컴퓨터과학", 0.9)));

        mockMvc.perform(get("/api/admin/catalog/embeddings/search?query=머신러닝&limit=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicId").value(30));
    }

    private static TopicResolutionView matched() {
        return new TopicResolutionView(
                1L,
                TopicInputResolution.MATCHED,
                TopicMatchSource.ALIAS,
                30L,
                "머신러닝",
                "machine-learning",
                "인공지능",
                "컴퓨터과학",
                false,
                List.of(),
                false,
                null);
    }

    private static TopicInputLogView logView() {
        return new TopicInputLogView(
                5L,
                "바이올린",
                TopicInputResolution.NEEDS_REVIEW,
                null,
                null,
                "바이올린",
                3,
                "user-1",
                false,
                null,
                null,
                null);
    }
}
