package com.mindplates.nextchapter.adapter.in.web.chapter;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.port.in.GetChapterUseCase;
import com.mindplates.nextchapter.application.chapter.view.ChapterBodyView;
import com.mindplates.nextchapter.application.chapter.view.ChapterVersionSummaryView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChapterAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("챕터 검수 API")
class ChapterAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetChapterUseCase getChapterUseCase;

    /** 응답이 블록 문서라는 것 자체가 계약이다 — 서버가 HTML 을 만들면 앱 확장 때 걸린다. */
    @Test
    @DisplayName("블록 문서를 그대로 내린다 — 타입과 속성이 살아 있다")
    void returnsBlockDocument() throws Exception {
        when(getChapterUseCase.currentBody(100L)).thenReturn(body());

        mockMvc.perform(get("/api/admin/chapters/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterKey").value("gradient-descent"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.blocks[0].type").value("HEADING"))
                .andExpect(jsonPath("$.data.blocks[1].id").value("b2"))
                .andExpect(jsonPath("$.data.blocks[2].attributes.spec").exists());
    }

    /** 신호가 "사용자가 실제로 소비한 버전"을 기록해야 하므로 응답에 버전이 실린다. */
    @Test
    @DisplayName("개선 여부와 변경 요약이 함께 나온다 — 배지의 근거")
    void carriesImprovementBadge() throws Exception {
        when(getChapterUseCase.currentBody(100L)).thenReturn(body());

        mockMvc.perform(get("/api/admin/chapters/100"))
                .andExpect(jsonPath("$.data.improvedFromPrevious").value(true))
                .andExpect(jsonPath("$.data.changeSummary").value("b2 설명 보강"));
    }

    @Test
    @DisplayName("임의 버전을 조회할 수 있다")
    void readsSpecificVersion() throws Exception {
        when(getChapterUseCase.bodyAt(100L, 1)).thenReturn(body());

        mockMvc.perform(get("/api/admin/chapters/100/versions/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이력은 본문 없이 무엇이 왜 바뀌었는지만 준다")
    void historyCarriesMetadataOnly() throws Exception {
        when(getChapterUseCase.history(100L))
                .thenReturn(List.of(
                        new ChapterVersionSummaryView(
                                2, ChapterVersionSource.REVISED_BY_SIGNAL, "b2 설명 보강", "signal-loop", 4, null),
                        new ChapterVersionSummaryView(1, ChapterVersionSource.GENERATED, null, "pipeline", 3, null)));

        mockMvc.perform(get("/api/admin/chapters/100/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].source").value("REVISED_BY_SIGNAL"))
                .andExpect(jsonPath("$.data[0].blockCount").value(4))
                .andExpect(jsonPath("$.data[0].blocks").doesNotExist());
    }

    @Test
    @DisplayName("없는 챕터·버전은 404 다")
    void missingIsNotFound() throws Exception {
        when(getChapterUseCase.currentBody(999L)).thenThrow(new EntityNotFoundException("챕터", 999L));

        mockMvc.perform(get("/api/admin/chapters/999")).andExpect(status().isNotFound());
    }

    private static ChapterBodyView body() {
        return new ChapterBodyView(
                100L,
                "gradient-descent",
                "경사하강법",
                "요약",
                2,
                List.of(
                        Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                        Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로"),
                        Block.figure("b3", Map.of("type", "plot"))),
                true,
                "b2 설명 보강",
                null);
    }
}
