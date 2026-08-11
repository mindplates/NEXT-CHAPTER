package com.mindplates.nextchapter.adapter.in.web.generation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.port.in.GetChapterOutlinesUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterOutlineView;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkeletonOutlineAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("개요·소견 검수 API")
class SkeletonOutlineAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetChapterOutlinesUseCase getChapterOutlinesUseCase;

    /** 소견이 본문 생성을 막지 않으므로 운영자가 보는 경로가 유일한 활용 수단이다. */
    @Test
    @DisplayName("개요와 소견을 함께 내린다")
    void returnsOutlinesWithFindings() throws Exception {
        when(getChapterOutlinesUseCase.bySkeletonId(5L))
                .thenReturn(new ChapterOutlineView(
                        5L,
                        List.of(
                                new ChapterOutlineView.Entry(
                                        100L, "loss-function", "손실함수", List.of("손실의 정의", "대표적인 손실함수")),
                                new ChapterOutlineView.Entry(101L, "gradient-descent", "경사하강법", List.of())),
                        List.of(new ChapterOutlineView.Finding(
                                OutlineFindingType.DUPLICATE,
                                List.of("loss-function", "gradient-descent"),
                                "두 챕터가 미분을 모두 설명한다"))));

        mockMvc.perform(get("/api/admin/skeletons/5/outlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outlines[0].points[0]").value("손실의 정의"))
                // 개요가 아직 없는 챕터는 빈 목록으로 보인다 — 생성 진행 중 상태다.
                .andExpect(jsonPath("$.data.outlines[1].points").isEmpty())
                .andExpect(jsonPath("$.data.findings[0].findingType").value("DUPLICATE"))
                .andExpect(jsonPath("$.data.findings[0].chapterKeys[1]").value("gradient-descent"));
    }

    @Test
    @DisplayName("소견이 없으면 빈 목록이다")
    void emptyFindings() throws Exception {
        when(getChapterOutlinesUseCase.bySkeletonId(5L)).thenReturn(new ChapterOutlineView(5L, List.of(), List.of()));

        mockMvc.perform(get("/api/admin/skeletons/5/outlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findings").isEmpty());
    }
}
