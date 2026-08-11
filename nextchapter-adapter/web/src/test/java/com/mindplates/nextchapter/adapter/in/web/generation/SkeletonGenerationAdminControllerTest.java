package com.mindplates.nextchapter.adapter.in.web.generation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.generation.port.in.GetGenerationProgressUseCase;
import com.mindplates.nextchapter.application.generation.port.in.StartSkeletonGenerationUseCase;
import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkeletonGenerationAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("뼈대 생성·진행률 API")
class SkeletonGenerationAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StartSkeletonGenerationUseCase startSkeletonGenerationUseCase;

    @MockitoBean
    GetGenerationProgressUseCase getGenerationProgressUseCase;

    @Test
    @DisplayName("생성 시작은 진행 상태를 돌려준다")
    void startReturnsProgress() throws Exception {
        when(startSkeletonGenerationUseCase.start(42L))
                .thenReturn(GenerationProgressView.of(5L, 42L, SkeletonStatus.GENERATING_GRAPH, 0, 0));

        mockMvc.perform(post("/api/admin/skeletons/topics/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skeletonId").value(5))
                .andExpect(jsonPath("$.data.status").value("GENERATING_GRAPH"))
                .andExpect(jsonPath("$.data.percent").value(0));
    }

    /** 퍼센트만 보여주면 "80%에서 한 시간"이 버그처럼 보인다 — 챕터 수를 함께 내린다. */
    @Test
    @DisplayName("진행률은 챕터 수와 본문 완료 수를 함께 준다")
    void progressCarriesChapterCounts() throws Exception {
        when(getGenerationProgressUseCase.bySkeletonId(5L))
                .thenReturn(GenerationProgressView.of(5L, 42L, SkeletonStatus.GENERATING_BODIES, 20, 10));

        mockMvc.perform(get("/api/admin/skeletons/5/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalChapters").value(20))
                .andExpect(jsonPath("$.data.chaptersWithBody").value(10))
                .andExpect(jsonPath("$.data.percent").value(50))
                .andExpect(jsonPath("$.data.published").value(false));
    }

    @Test
    @DisplayName("주제 ID 로도 진행률을 볼 수 있다")
    void progressByTopic() throws Exception {
        when(getGenerationProgressUseCase.byTopicId(42L))
                .thenReturn(GenerationProgressView.of(5L, 42L, SkeletonStatus.PUBLISHED, 20, 20));

        mockMvc.perform(get("/api/admin/skeletons/topics/42/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percent").value(100))
                .andExpect(jsonPath("$.data.published").value(true));
    }

    @Test
    @DisplayName("뼈대가 없으면 404 다")
    void missingSkeletonIsNotFound() throws Exception {
        when(getGenerationProgressUseCase.bySkeletonId(999L)).thenThrow(new EntityNotFoundException("뼈대", 999L));

        mockMvc.perform(get("/api/admin/skeletons/999/progress")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("실패는 진행률에 숫자를 남기지 않는다 — 멈춘 것과 느린 것이 구분돼야 한다")
    void failedCarriesNoPercent() throws Exception {
        when(getGenerationProgressUseCase.bySkeletonId(5L))
                .thenReturn(GenerationProgressView.of(5L, 42L, SkeletonStatus.FAILED, 20, 12));

        mockMvc.perform(get("/api/admin/skeletons/5/progress"))
                .andExpect(jsonPath("$.data.failed").value(true))
                .andExpect(jsonPath("$.data.percent").value(0));
    }
}
