package com.mindplates.nextchapter.adapter.in.web.chapter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.port.in.ExploreChapterGraphUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterNeighborhoodView;
import com.mindplates.nextchapter.application.chapter.view.ChapterNodeView;
import com.mindplates.nextchapter.application.chapter.view.SkeletonEntryView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChapterExploreController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("그래프 탐색 API")
class ChapterExploreControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExploreChapterGraphUseCase exploreChapterGraphUseCase;

    @Test
    @DisplayName("주제로 진입점을 조회한다")
    void entryPointsByTopic() throws Exception {
        when(exploreChapterGraphUseCase.entryPointsByTopicId(77L))
                .thenReturn(new SkeletonEntryView(5L, 77L, List.of(new ChapterNodeView(100L, "a", "가", 1))));

        mockMvc.perform(get("/api/topics/77/entry-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skeletonId").value(5))
                .andExpect(jsonPath("$.data.entryPoints[0].chapterId").value(100))
                .andExpect(jsonPath("$.data.entryPoints[0].chapterKey").value("a"));
    }

    @Test
    @DisplayName("뼈대로도 진입점을 조회한다")
    void entryPointsBySkeleton() throws Exception {
        when(exploreChapterGraphUseCase.entryPointsBySkeletonId(5L))
                .thenReturn(new SkeletonEntryView(5L, 77L, List.of()));

        mockMvc.perform(get("/api/skeletons/5/entry-points")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이웃 조회는 중심·이웃·간선을 함께 내린다")
    void neighbors() throws Exception {
        when(exploreChapterGraphUseCase.neighbors(100L, 2))
                .thenReturn(new ChapterNeighborhoodView(
                        new ChapterNodeView(100L, "a", "가", 2),
                        2,
                        List.of(new ChapterNodeView(101L, "b", "나", 1)),
                        List.of(new ChapterNeighborhoodView.Relation(100L, 101L, ChapterRelationType.PREREQUISITE))));

        mockMvc.perform(get("/api/chapters/100/neighbors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.center.chapterKey").value("a"))
                .andExpect(jsonPath("$.data.depth").value(2))
                .andExpect(jsonPath("$.data.neighbors[0].chapterId").value(101))
                .andExpect(jsonPath("$.data.relations[0].relationType").value("PREREQUISITE"));
    }

    /** 홉 수를 명시하지 않았을 때의 기본값이 계약이다 — 바뀌면 클라이언트가 보던 범위가 조용히 달라진다. */
    @Test
    @DisplayName("depth 를 주지 않으면 2홉이다")
    void defaultDepthIsTwo() throws Exception {
        when(exploreChapterGraphUseCase.neighbors(100L, 2))
                .thenReturn(
                        new ChapterNeighborhoodView(new ChapterNodeView(100L, "a", "가", 2), 2, List.of(), List.of()));

        mockMvc.perform(get("/api/chapters/100/neighbors")).andExpect(status().isOk());

        verify(exploreChapterGraphUseCase).neighbors(100L, 2);
    }

    @Test
    @DisplayName("depth 를 넘기면 그대로 전달한다 — 접는 것은 어댑터의 몫이다")
    void passesDepthThrough() throws Exception {
        when(exploreChapterGraphUseCase.neighbors(100L, 99))
                .thenReturn(
                        new ChapterNeighborhoodView(new ChapterNodeView(100L, "a", "가", 2), 3, List.of(), List.of()));

        mockMvc.perform(get("/api/chapters/100/neighbors").param("depth", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depth").value(3));
    }

    /** 공개되지 않은 뼈대는 존재 여부를 알려주지 않는다 — 404 다. */
    @Test
    @DisplayName("공개되지 않은 뼈대는 404 다")
    void unpublishedIsNotFound() throws Exception {
        when(exploreChapterGraphUseCase.entryPointsBySkeletonId(5L))
                .thenThrow(new EntityNotFoundException("공개된 뼈대", 5L));

        mockMvc.perform(get("/api/skeletons/5/entry-points")).andExpect(status().isNotFound());
    }
}
