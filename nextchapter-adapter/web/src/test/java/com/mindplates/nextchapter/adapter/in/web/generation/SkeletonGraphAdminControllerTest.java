package com.mindplates.nextchapter.adapter.in.web.generation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.port.in.GetSkeletonGraphUseCase;
import com.mindplates.nextchapter.application.chapter.view.SkeletonGraphView;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkeletonGraphAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("챕터 그래프 검수 API")
class SkeletonGraphAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetSkeletonGraphUseCase getSkeletonGraphUseCase;

    /** 관계를 키로 내리는 이유는 검수 화면과 클라이언트가 사람이 읽는 식별자로 노드를 찾기 때문이다. */
    @Test
    @DisplayName("관계를 키로 내리고 진입점을 함께 준다")
    void returnsGraphKeyedByChapterKey() throws Exception {
        when(getSkeletonGraphUseCase.bySkeletonId(5L))
                .thenReturn(new SkeletonGraphView(
                        5L,
                        List.of(
                                new SkeletonGraphView.Node(100L, "loss-function", "손실함수", "요약", true, 2),
                                new SkeletonGraphView.Node(101L, "gradient-descent", "경사하강법", null, false, 0)),
                        List.of(new SkeletonGraphView.Edge(
                                "loss-function", "gradient-descent", ChapterRelationType.PREREQUISITE)),
                        List.of("loss-function")));

        mockMvc.perform(get("/api/admin/skeletons/5/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapters[0].chapterKey").value("loss-function"))
                .andExpect(jsonPath("$.data.chapters[0].hasBody").value(true))
                .andExpect(jsonPath("$.data.chapters[1].version").value(0))
                .andExpect(jsonPath("$.data.relations[0].fromKey").value("loss-function"))
                .andExpect(jsonPath("$.data.relations[0].relationType").value("PREREQUISITE"))
                .andExpect(jsonPath("$.data.entryPointKeys[0]").value("loss-function"));
    }

    @Test
    @DisplayName("그래프가 아직 없으면 빈 목록이 나온다 — 생성 전 상태다")
    void emptyGraphBeforeGeneration() throws Exception {
        when(getSkeletonGraphUseCase.bySkeletonId(5L))
                .thenReturn(new SkeletonGraphView(5L, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/admin/skeletons/5/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapters").isEmpty());
    }
}
