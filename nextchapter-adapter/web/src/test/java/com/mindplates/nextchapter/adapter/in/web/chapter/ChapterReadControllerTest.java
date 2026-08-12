package com.mindplates.nextchapter.adapter.in.web.chapter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.chapter.port.in.ComposeChapterUseCase;
import com.mindplates.nextchapter.application.chapter.view.BlockView;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChapterReadController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("사용자 챕터 조회 API")
class ChapterReadControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ComposeChapterUseCase composeChapterUseCase;

    private static ComposedChapterView view(DeliveryFormat format) {
        return new ComposedChapterView(
                100L,
                "gradient-descent",
                "경사하강법",
                "요약",
                2,
                format,
                BlockView.forLearner(List.of(
                        Block.text("b1", BlockType.HEADING, "경사하강법이란"), Block.figure("b2", Map.of("type", "plot")))),
                false,
                true,
                "b2 설명 보강",
                null);
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    /** 응답이 블록 목록이라는 것 자체가 계약이다 — 서버가 HTML 을 만들면 앱 확장 때 API 를 다시 만든다. */
    @Test
    @DisplayName("블록 목록을 내린다 — 타입과 속성이 살아 있다")
    void returnsBlocks() throws Exception {
        when(composeChapterUseCase.compose(42L, 100L, DeliveryFormat.WEB)).thenReturn(view(DeliveryFormat.WEB));

        mockMvc.perform(get("/api/chapters/100").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.format").value("WEB"))
                .andExpect(jsonPath("$.data.blocks[0].type").value("HEADING"))
                .andExpect(jsonPath("$.data.blocks[1].attributes.spec").exists())
                .andExpect(jsonPath("$.data.improvedFromPrevious").value(true));
    }

    @Test
    @DisplayName("형태를 주지 않으면 WEB 이다")
    void defaultFormatIsWeb() throws Exception {
        when(composeChapterUseCase.compose(42L, 100L, DeliveryFormat.WEB)).thenReturn(view(DeliveryFormat.WEB));

        mockMvc.perform(get("/api/chapters/100").principal(principal("42"))).andExpect(status().isOk());

        verify(composeChapterUseCase).compose(42L, 100L, DeliveryFormat.WEB);
    }

    @Test
    @DisplayName("형태를 지정할 수 있다")
    void acceptsFormat() throws Exception {
        when(composeChapterUseCase.compose(42L, 100L, DeliveryFormat.VIDEO)).thenReturn(view(DeliveryFormat.VIDEO));

        mockMvc.perform(get("/api/chapters/100").param("format", "VIDEO").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("VIDEO"));
    }

    @Test
    @DisplayName("모르는 형태는 400 이다")
    void rejectsUnknownFormat() throws Exception {
        mockMvc.perform(get("/api/chapters/100").param("format", "HOLOGRAM").principal(principal("42")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("공개되지 않은 뼈대의 챕터는 404 다")
    void unpublishedIsNotFound() throws Exception {
        when(composeChapterUseCase.compose(42L, 100L, DeliveryFormat.WEB))
                .thenThrow(new EntityNotFoundException("공개된 뼈대", 5L));

        mockMvc.perform(get("/api/chapters/100").principal(principal("42"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("사용자 토큰이 아니면 401 이다")
    void rejectsNonUserToken() throws Exception {
        mockMvc.perform(get("/api/chapters/100").principal(principal("admin@nextchapter.local")))
                .andExpect(status().isUnauthorized());
    }
}
