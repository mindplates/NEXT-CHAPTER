package com.mindplates.nextchapter.adapter.in.web.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.catalog.port.in.LearnerTopicEntryUseCase;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.common.exception.RateLimitExceededException;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M1 이 관리자 경로에만 두었던 것을 여기서 연다. 확인할 것은 <b>호출자가 토큰에서 온다</b>는 점과
 * 리밋 초과가 429 로 나간다는 점이다 — 400 으로 접으면 클라이언트가 즉시 재시도해 한도를 계속 소진한다.
 */
@WebMvcTest(LearnerTopicController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("사용자 주제 입력 API")
class LearnerTopicControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LearnerTopicEntryUseCase learnerTopicEntryUseCase;

    private static TopicResolutionView matched() {
        return new TopicResolutionView(
                11L,
                TopicInputResolution.MATCHED,
                TopicMatchSource.ALIAS,
                7L,
                "머신러닝",
                "machine-learning",
                "인공지능",
                "컴퓨터과학",
                true,
                List.of(),
                false,
                null);
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    @Test
    @DisplayName("입력을 해소하고 호출자를 토큰에서 가져온다")
    void resolvesWithTokenSubject() throws Exception {
        when(learnerTopicEntryUseCase.resolve(42L, "머신러닝")).thenReturn(matched());

        mockMvc.perform(post("/api/topics/resolve")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"머신러닝\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value(7))
                .andExpect(jsonPath("$.data.skeletonExists").value(true));

        verify(learnerTopicEntryUseCase).resolve(42L, "머신러닝");
    }

    @Test
    @DisplayName("리밋을 넘으면 429 다")
    void rateLimitIsTooManyRequests() throws Exception {
        when(learnerTopicEntryUseCase.resolve(anyLong(), any()))
                .thenThrow(new RateLimitExceededException("주제 입력이 너무 잦습니다."));

        mockMvc.perform(post("/api/topics/resolve")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"머신러닝\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("후보 확정에도 호출자가 실린다")
    void confirmCarriesCaller() throws Exception {
        when(learnerTopicEntryUseCase.confirmCandidate(42L, 11L, 7L)).thenReturn(matched());

        mockMvc.perform(post("/api/topics/resolve/11/confirm")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":7}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("후보 거절에도 호출자가 실린다")
    void rejectCarriesCaller() throws Exception {
        when(learnerTopicEntryUseCase.rejectCandidates(42L, 11L)).thenReturn(matched());

        mockMvc.perform(post("/api/topics/resolve/11/reject").principal(principal("42")))
                .andExpect(status().isOk());
    }

    /** 관리자 토큰이 새어 들어오면 subject 가 이메일이다. 500 이 아니라 401 이어야 한다. */
    @Test
    @DisplayName("사용자 토큰이 아니면 401 이다")
    void rejectsNonUserToken() throws Exception {
        mockMvc.perform(post("/api/topics/resolve")
                        .principal(principal("admin@nextchapter.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"머신러닝\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("빈 입력은 400 이다")
    void rejectsBlankInput() throws Exception {
        mockMvc.perform(post("/api/topics/resolve")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
