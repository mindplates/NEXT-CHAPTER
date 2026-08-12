package com.mindplates.nextchapter.adapter.in.web.layer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.view.BlockView;
import com.mindplates.nextchapter.application.layer.port.in.GetChapterLayerUseCase;
import com.mindplates.nextchapter.application.layer.port.in.RecommendNextChapterUseCase;
import com.mindplates.nextchapter.application.layer.port.in.RequestSupplementUseCase;
import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.NextChapterView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;
import com.mindplates.nextchapter.application.layer.view.SupplementView;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.layer.model.RecommendationReason;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LearnerLayerController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("내 학습 상태 API")
class LearnerLayerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetChapterLayerUseCase getChapterLayerUseCase;

    @MockitoBean
    RecommendNextChapterUseCase recommendNextChapterUseCase;

    @MockitoBean
    RequestSupplementUseCase requestSupplementUseCase;

    private static ChapterUnderstandingView chapter(Long id, UnderstandingLevel level) {
        return new ChapterUnderstandingView(id, "a", "가", 4, 1, 2, 60, level, false, null);
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    @Test
    @DisplayName("뼈대 전체의 진행과 챕터별 이해도를 내린다")
    void skeletonLayer() throws Exception {
        when(getChapterLayerUseCase.bySkeletonId(42L, 5L))
                .thenReturn(new SkeletonLayerView(5L, 3, 1, 0, List.of(chapter(100L, UnderstandingLevel.STRUGGLING))));

        mockMvc.perform(get("/api/skeletons/5/my-layer").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalChapters").value(3))
                .andExpect(jsonPath("$.data.visitedChapters").value(1))
                .andExpect(jsonPath("$.data.chapters[0].level").value("STRUGGLING"))
                .andExpect(jsonPath("$.data.chapters[0].attempts").value(4));
    }

    /** 경로에 사용자 ID 를 두면 남의 레이어를 조회할 수 있다 — 다른 사람의 오답과 질문을 읽는 것이다. */
    @Test
    @DisplayName("사용자는 토큰에서만 온다")
    void userComesFromToken() throws Exception {
        when(getChapterLayerUseCase.byChapterId(42L, 100L)).thenReturn(chapter(100L, UnderstandingLevel.LEARNING));

        mockMvc.perform(get("/api/chapters/100/my-layer").principal(principal("42")))
                .andExpect(status().isOk());

        verify(getChapterLayerUseCase).byChapterId(42L, 100L);
    }

    /** 이유를 함께 내려야 사용자가 "왜 앞으로 안 가고 뒤로 가나"에 납득할 수 있다. */
    @Test
    @DisplayName("다음 챕터 후보에 이유와 이해도가 실린다")
    void nextCandidates() throws Exception {
        when(recommendNextChapterUseCase.next(42L, 100L))
                .thenReturn(new NextChapterView(
                        100L,
                        UnderstandingLevel.STRUGGLING,
                        List.of(new NextChapterView.Candidate(
                                10L,
                                "prev",
                                "선행 챕터",
                                RecommendationReason.PREREQUISITE_GAP,
                                UnderstandingLevel.LEARNING,
                                false))));

        mockMvc.perform(get("/api/chapters/100/next").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentLevel").value("STRUGGLING"))
                .andExpect(jsonPath("$.data.candidates[0].chapterId").value(10))
                .andExpect(jsonPath("$.data.candidates[0].reason").value("PREREQUISITE_GAP"));
    }

    /** 새로 만든 것과 이미 있던 것을 상태 코드로 구분한다 — 그 차이가 비용 방어선이 동작하는지의 확인이다. */
    @Test
    @DisplayName("보충 블록을 새로 만들면 201, 이미 있으면 200 이다")
    void supplementStatus() throws Exception {
        BlockView block = BlockView.forLearner(
                com.mindplates.nextchapter.domain.chapter.model.Block.text("s1", BlockType.PARAGRAPH, "예를 들어"));
        when(requestSupplementUseCase.request(42L, 100L, "b2")).thenReturn(new SupplementView("b2", block, false));

        mockMvc.perform(post("/api/chapters/100/blocks/b2/supplement").principal(principal("42")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.anchorBlockId").value("b2"))
                .andExpect(jsonPath("$.data.block.id").value("s1"));

        when(requestSupplementUseCase.request(42L, 100L, "b2")).thenReturn(new SupplementView("b2", block, true));

        mockMvc.perform(post("/api/chapters/100/blocks/b2/supplement").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused").value(true));
    }

    @Test
    @DisplayName("사용자 토큰이 아니면 401 이다")
    void rejectsNonUserToken() throws Exception {
        mockMvc.perform(get("/api/skeletons/5/my-layer").principal(principal("admin@nextchapter.local")))
                .andExpect(status().isUnauthorized());
    }
}
