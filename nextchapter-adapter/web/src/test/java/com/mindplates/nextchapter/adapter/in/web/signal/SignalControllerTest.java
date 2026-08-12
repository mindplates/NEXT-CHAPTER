package com.mindplates.nextchapter.adapter.in.web.signal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.signal.port.in.RecordSignalUseCase;
import com.mindplates.nextchapter.application.signal.port.in.command.RecordSignalCommand;
import com.mindplates.nextchapter.application.signal.view.SignalView;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SignalController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("신호 기록 API")
class SignalControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecordSignalUseCase recordSignalUseCase;

    private static SignalView view(SignalType type, String blockId) {
        return new SignalView(
                7L,
                100L,
                2,
                blockId,
                DeliveryFormat.WEB,
                type,
                type == SignalType.QUIZ_ANSWER ? Boolean.FALSE : null,
                LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    /** 블록 ID·형태·소비한 버전이 요청에 실려야 한다 — 셋 중 하나라도 빠지면 집계 정밀도가 떨어진다. */
    @Test
    @DisplayName("퀴즈 응답이 블록·형태·버전을 달고 커맨드로 넘어간다")
    void recordsQuizAnswer() throws Exception {
        when(recordSignalUseCase.record(anyLong(), any())).thenReturn(view(SignalType.QUIZ_ANSWER, "b6"));

        mockMvc.perform(
                        post("/api/chapters/100/signals")
                                .principal(principal("42"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"chapterVersion":2,"blockId":"b6","format":"WEB","type":"QUIZ_ANSWER",
                                 "payload":{"choice":"가"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.blockId").value("b6"))
                // 채점 결과는 되돌려준다 — 정답을 내려 주지 않으므로 클라이언트가 알 방법이 없다.
                .andExpect(jsonPath("$.data.correct").value(false));

        ArgumentCaptor<RecordSignalCommand> command = ArgumentCaptor.forClass(RecordSignalCommand.class);
        verify(recordSignalUseCase).record(org.mockito.ArgumentMatchers.eq(42L), command.capture());
        assertCommand(command.getValue());
    }

    private static void assertCommand(RecordSignalCommand command) {
        org.assertj.core.api.Assertions.assertThat(command.chapterId()).isEqualTo(100L);
        org.assertj.core.api.Assertions.assertThat(command.chapterVersion()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(command.blockId()).isEqualTo("b6");
        org.assertj.core.api.Assertions.assertThat(command.format()).isEqualTo(DeliveryFormat.WEB);
        org.assertj.core.api.Assertions.assertThat(command.type()).isEqualTo(SignalType.QUIZ_ANSWER);
        org.assertj.core.api.Assertions.assertThat(command.payload()).isEqualTo(Map.of("choice", "가"));
    }

    @Test
    @DisplayName("질문도 같은 엔드포인트를 쓴다")
    void recordsQuestion() throws Exception {
        when(recordSignalUseCase.record(anyLong(), any())).thenReturn(view(SignalType.QUESTION, "b2"));

        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterVersion\":2,\"blockId\":\"b2\",\"type\":\"QUESTION\","
                                + "\"payload\":{\"text\":\"왜 이렇게 되나요?\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("QUESTION"));
    }

    /** 소비한 버전이 없으면 어느 버전에 대한 반응인지 알 수 없다 — 저장하면 집계의 기준선이 사라진다. */
    @Test
    @DisplayName("소비한 버전이 없으면 400 이다")
    void rejectsMissingVersion() throws Exception {
        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockId\":\"b6\",\"type\":\"QUIZ_ANSWER\",\"payload\":{\"correct\":true}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("종류가 없으면 400 이다")
    void rejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterVersion\":2,\"blockId\":\"b6\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("모르는 종류는 400 이다")
    void rejectsUnknownType() throws Exception {
        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterVersion\":2,\"blockId\":\"b6\",\"type\":\"SHRUG\",\"payload\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("그 버전에 없는 블록은 400 이다")
    void rejectsUnknownBlock() throws Exception {
        when(recordSignalUseCase.record(anyLong(), any()))
                .thenThrow(new InvalidOperationException("그 버전에 없는 블록입니다: v2 b99"));

        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterVersion\":2,\"blockId\":\"b99\",\"type\":\"QUIZ_ANSWER\","
                                + "\"payload\":{\"correct\":true}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사용자 토큰이 아니면 401 이다")
    void rejectsNonUserToken() throws Exception {
        mockMvc.perform(post("/api/chapters/100/signals")
                        .principal(principal("admin@nextchapter.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterVersion\":2,\"blockId\":\"b6\",\"type\":\"QUIZ_ANSWER\","
                                + "\"payload\":{\"correct\":true}}"))
                .andExpect(status().isUnauthorized());
    }
}
