package com.mindplates.nextchapter.adapter.in.web.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.admin.port.in.GetCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateCollectiveThresholdSettingsUseCase;
import com.mindplates.nextchapter.application.admin.view.CollectiveThresholdView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectiveThresholdAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("집단 루프 임계치 관리 API")
class CollectiveThresholdAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetCollectiveThresholdSettingsUseCase getCollectiveThresholdSettingsUseCase;

    @MockitoBean
    UpdateCollectiveThresholdSettingsUseCase updateCollectiveThresholdSettingsUseCase;

    @Test
    @DisplayName("현재 임계치를 내린다")
    void returnsCurrent() throws Exception {
        when(getCollectiveThresholdSettingsUseCase.current())
                .thenReturn(new CollectiveThresholdView(5, 20, 40, "admin"));

        mockMvc.perform(get("/api/admin/collective/threshold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionThreshold").value(5))
                .andExpect(jsonPath("$.data.minAttempts").value(20))
                .andExpect(jsonPath("$.data.wrongRatePercent").value(40));
    }

    @Test
    @DisplayName("변경한다")
    void updatesThreshold() throws Exception {
        when(updateCollectiveThresholdSettingsUseCase.update(10, 30, 50, null))
                .thenReturn(new CollectiveThresholdView(10, 30, 50, null));

        mockMvc.perform(put("/api/admin/collective/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionThreshold\":10,\"minAttempts\":30,\"wrongRatePercent\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionThreshold").value(10))
                .andExpect(jsonPath("$.data.minAttempts").value(30))
                .andExpect(jsonPath("$.data.wrongRatePercent").value(50));
    }

    /** 0 이하나 100 초과는 의미가 없다 — 무제한이 없는 값이라 항상 범위를 검증해야 한다. */
    @Test
    @DisplayName("범위를 벗어난 오답률은 400 이다")
    void rejectsOutOfRangeWrongRate() throws Exception {
        mockMvc.perform(put("/api/admin/collective/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionThreshold\":5,\"minAttempts\":20,\"wrongRatePercent\":101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("값이 없으면 400 이다")
    void rejectsMissingValue() throws Exception {
        mockMvc.perform(put("/api/admin/collective/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionThreshold\":5,\"minAttempts\":20}"))
                .andExpect(status().isBadRequest());
    }
}
