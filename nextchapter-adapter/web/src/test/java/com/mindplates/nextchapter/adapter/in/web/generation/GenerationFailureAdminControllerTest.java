package com.mindplates.nextchapter.adapter.in.web.generation;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.generation.port.in.ManageGenerationFailureUseCase;
import com.mindplates.nextchapter.application.generation.view.GenerationFailureView;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GenerationFailureAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("생성 실패 운영자 큐 API")
class GenerationFailureAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManageGenerationFailureUseCase manageGenerationFailureUseCase;

    private static GenerationFailureView view(GenerationFailureStatus status) {
        return new GenerationFailureView(
                77L,
                5L,
                101L,
                GenerationStage.BODY,
                "com.example.VendorTimeout",
                "타임아웃",
                3,
                status,
                null,
                null,
                LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    /** 큐의 기본 질문은 "지금 손댈 것이 무엇인가"다. */
    @Test
    @DisplayName("기본 목록은 OPEN 만 본다")
    void defaultsToOpen() throws Exception {
        when(manageGenerationFailureUseCase.list(GenerationFailureStatus.OPEN))
                .thenReturn(List.of(view(GenerationFailureStatus.OPEN)));

        mockMvc.perform(get("/api/admin/generation-failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].stage").value("BODY"))
                .andExpect(jsonPath("$.data[0].attempts").value(3));
    }

    /** 목록 응답에 페이로드가 없다 — 프롬프트와 챕터 내용이 섞여 있다. */
    @Test
    @DisplayName("응답에 페이로드를 싣지 않는다")
    void omitsPayload() throws Exception {
        when(manageGenerationFailureUseCase.list(GenerationFailureStatus.OPEN))
                .thenReturn(List.of(view(GenerationFailureStatus.OPEN)));

        mockMvc.perform(get("/api/admin/generation-failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].payload").doesNotExist());
    }

    /** 처리된 항목까지 훑어야 같은 원인의 반복이 보인다. */
    @Test
    @DisplayName("ALL 은 상태 없이 전체를 조회한다")
    void allMeansNoFilter() throws Exception {
        when(manageGenerationFailureUseCase.list(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/generation-failures").param("status", "all"))
                .andExpect(status().isOk());

        verify(manageGenerationFailureUseCase).list(isNull());
    }

    @Test
    @DisplayName("모르는 상태 값은 400 이다")
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/admin/generation-failures").param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("재시도는 RETRIED 를 돌려준다 — 성공을 뜻하지 않는다")
    void retryReturnsRetried() throws Exception {
        when(manageGenerationFailureUseCase.retry(77L, null)).thenReturn(view(GenerationFailureStatus.RETRIED));

        mockMvc.perform(post("/api/admin/generation-failures/77/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETRIED"));
    }

    @Test
    @DisplayName("닫으면 RESOLVED 가 된다")
    void resolveClosesItem() throws Exception {
        when(manageGenerationFailureUseCase.resolve(77L, null)).thenReturn(view(GenerationFailureStatus.RESOLVED));

        mockMvc.perform(post("/api/admin/generation-failures/77/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("뼈대별로 조회한다")
    void listsBySkeleton() throws Exception {
        when(manageGenerationFailureUseCase.bySkeletonId(5L)).thenReturn(List.of(view(GenerationFailureStatus.OPEN)));

        mockMvc.perform(get("/api/admin/generation-failures/skeletons/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skeletonId").value(5));
    }
}
