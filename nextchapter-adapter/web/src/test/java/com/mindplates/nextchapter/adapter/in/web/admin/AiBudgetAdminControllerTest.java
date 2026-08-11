package com.mindplates.nextchapter.adapter.in.web.admin;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.admin.port.in.GetAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiBudgetUseCase;
import com.mindplates.nextchapter.application.admin.view.AiBudgetView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiBudgetAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("예산 상한 관리 API")
class AiBudgetAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetAiBudgetUseCase getAiBudgetUseCase;

    @MockitoBean
    UpdateAiBudgetUseCase updateAiBudgetUseCase;

    /** 상한만 내리면 "지금 얼마나 남았나"를 알 수 없다. 조정 판단에는 둘이 함께 필요하다. */
    @Test
    @DisplayName("상한과 오늘 사용량을 함께 내린다")
    void returnsLimitsWithUsage() throws Exception {
        when(getAiBudgetUseCase.current()).thenReturn(new AiBudgetView(1000000L, 20000000L, 4200L, "admin"));

        mockMvc.perform(get("/api/admin/ai/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perSkeletonTokenLimit").value(1000000L))
                .andExpect(jsonPath("$.data.dailyTokenLimit").value(20000000L))
                .andExpect(jsonPath("$.data.dailyUsedTokens").value(4200L));
    }

    @Test
    @DisplayName("무제한은 null 로 내려간다")
    void unlimitedIsNull() throws Exception {
        when(getAiBudgetUseCase.current()).thenReturn(new AiBudgetView(null, null, 0L, null));

        mockMvc.perform(get("/api/admin/ai/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perSkeletonTokenLimit").doesNotExist())
                .andExpect(jsonPath("$.data.dailyTokenLimit").doesNotExist());
    }

    @Test
    @DisplayName("뼈대별 사용량을 내린다")
    void returnsSkeletonUsage() throws Exception {
        when(getAiBudgetUseCase.usedTokensBySkeleton(5L)).thenReturn(12345L);

        mockMvc.perform(get("/api/admin/ai/budget/skeletons/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedTokens").value(12345L));
    }

    /** null 은 "안 보냈다"가 아니라 "무제한으로 바꿔라"다. 두 값을 항상 함께 보낸다. */
    @Test
    @DisplayName("null 을 보내면 무제한으로 바꾼다")
    void updatesToUnlimited() throws Exception {
        when(updateAiBudgetUseCase.update(isNull(), org.mockito.ArgumentMatchers.eq(50000L), isNull()))
                .thenReturn(new AiBudgetView(null, 50000L, 0L, null));

        mockMvc.perform(put("/api/admin/ai/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perSkeletonTokenLimit\":null,\"dailyTokenLimit\":50000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perSkeletonTokenLimit").doesNotExist())
                .andExpect(jsonPath("$.data.dailyTokenLimit").value(50000L));
    }

    /** 0 이나 음수를 받으면 상한이 사실상 생성 전면 차단이 된다. 무제한은 null 로만 표현한다. */
    @Test
    @DisplayName("0 이하 상한은 400 이다")
    void rejectsNonPositiveLimit() throws Exception {
        mockMvc.perform(put("/api/admin/ai/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perSkeletonTokenLimit\":0,\"dailyTokenLimit\":100}"))
                .andExpect(status().isBadRequest());
    }
}
