package com.mindplates.nextchapter.adapter.in.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.admin.port.in.AdminLoginUseCase;
import com.mindplates.nextchapter.application.admin.port.in.CreateAdminAccountUseCase;
import com.mindplates.nextchapter.application.admin.port.in.GetAdminAccountUseCase;
import com.mindplates.nextchapter.application.admin.port.in.GetAiSettingsUseCase;
import com.mindplates.nextchapter.application.admin.port.in.ManageAiCredentialUseCase;
import com.mindplates.nextchapter.application.admin.port.in.UpdateAiStageSettingUseCase;
import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.application.admin.view.AiCredentialView;
import com.mindplates.nextchapter.application.admin.view.AiStageSettingView;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminAuthController.class, AdminAccountAdminController.class, AiSettingsAdminController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("관리자 인증·계정·AI 설정 API")
class AdminApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminLoginUseCase adminLoginUseCase;

    @MockitoBean
    GetAdminAccountUseCase getAdminAccountUseCase;

    @MockitoBean
    CreateAdminAccountUseCase createAdminAccountUseCase;

    @MockitoBean
    GetAiSettingsUseCase getAiSettingsUseCase;

    @MockitoBean
    UpdateAiStageSettingUseCase updateAiStageSettingUseCase;

    @MockitoBean
    ManageAiCredentialUseCase manageAiCredentialUseCase;

    @Test
    @DisplayName("로그인은 토큰과 만료 시각을 준다")
    void loginReturnsToken() throws Exception {
        when(adminLoginUseCase.login(any()))
                .thenReturn(new AdminTokenView("jwt-value", "Bearer", Instant.parse("2026-08-12T00:00:00Z")));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@nextchapter.local\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("로그인 실패는 401 이다 — 400 과 나누는 이유는 클라이언트의 대응이 다르기 때문이다")
    void loginFailureIsUnauthorized() throws Exception {
        when(adminLoginUseCase.login(any())).thenThrow(new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@nextchapter.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("계정 응답에 비밀번호 해시가 없다")
    void accountResponseHasNoPasswordHash() throws Exception {
        when(getAdminAccountUseCase.list()).thenReturn(List.of(account()));

        mockMvc.perform(get("/api/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("admin@nextchapter.local"))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("계정 생성은 201 이고, 짧은 비밀번호는 400 이다")
    void createAccount() throws Exception {
        when(createAdminAccountUseCase.create(any())).thenReturn(account());

        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"name\":\"관리자\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isCreated());

        when(createAdminAccountUseCase.create(any())).thenThrow(new InvalidOperationException("비밀번호는 12자 이상이어야 합니다."));

        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"name\":\"관리자\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식이 아니면 필드 오류를 준다")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"name\":\"관리자\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("단계 설정 조회에 준비 상태가 함께 나온다 — 키 없이 설정만 맞춘 상태가 흔하다")
    void stageSettingsExposeReadiness() throws Exception {
        when(getAiSettingsUseCase.stageSettings())
                .thenReturn(List.of(new AiStageSettingView(
                        AiStage.SKELETON_BODY, AiVendor.ANTHROPIC, "claude-opus-5", true, false, null, null)));

        mockMvc.perform(get("/api/admin/ai/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].model").value("claude-opus-5"))
                .andExpect(jsonPath("$.data[0].vendorSupported").value(true))
                .andExpect(jsonPath("$.data[0].credentialRegistered").value(false));
    }

    @Test
    @DisplayName("지원 벤더 목록이 선택지로 내려간다")
    void listsSupportedVendors() throws Exception {
        when(getAiSettingsUseCase.supportedVendors()).thenReturn(List.of(AiVendor.ANTHROPIC, AiVendor.VOYAGE));

        mockMvc.perform(get("/api/admin/ai/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("ANTHROPIC"))
                .andExpect(jsonPath("$.data[1]").value("VOYAGE"));
    }

    @Test
    @DisplayName("단계 설정 변경은 경로의 단계를 그대로 쓴다")
    void updateStageUsesPathStage() throws Exception {
        when(updateAiStageSettingUseCase.update(eq(AiStage.TOPIC_MAPPING), any()))
                .thenReturn(new AiStageSettingView(
                        AiStage.TOPIC_MAPPING, AiVendor.ANTHROPIC, "claude-sonnet-5", true, true, null, null));

        mockMvc.perform(put("/api/admin/ai/stages/TOPIC_MAPPING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendor\":\"ANTHROPIC\",\"model\":\"claude-sonnet-5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("TOPIC_MAPPING"));
    }

    @Test
    @DisplayName("미지원 벤더 지정은 400 이다 — 저장은 되고 나중에 실패하는 쪽이 더 나쁘다")
    void rejectsUnsupportedVendor() throws Exception {
        when(updateAiStageSettingUseCase.update(any(), any()))
                .thenThrow(new InvalidOperationException("OPENAI 는 지원 어댑터가 없습니다."));

        mockMvc.perform(put("/api/admin/ai/stages/SKELETON_BODY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendor\":\"OPENAI\",\"model\":\"gpt-x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("키 등록 응답에는 힌트만 있다 — 평문을 돌려주는 경로가 없다")
    void credentialResponseExposesOnlyHint() throws Exception {
        when(manageAiCredentialUseCase.register(any()))
                .thenReturn(new AiCredentialView(AiVendor.ANTHROPIC, "…1234", null));
        when(getAiSettingsUseCase.credentials())
                .thenReturn(List.of(new AiCredentialView(AiVendor.ANTHROPIC, "…1234", null)));

        mockMvc.perform(post("/api/admin/ai/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendor\":\"ANTHROPIC\",\"apiKey\":\"sk-ant-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keyHint").value("…1234"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());

        mockMvc.perform(get("/api/admin/ai/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyHint").value("…1234"));

        mockMvc.perform(delete("/api/admin/ai/credentials/ANTHROPIC")).andExpect(status().isOk());
        verify(manageAiCredentialUseCase).remove(AiVendor.ANTHROPIC);
    }

    private static AdminAccount account() {
        return new AdminAccount(
                1L, "admin@nextchapter.local", "관리자", "{bcrypt}hash", AdminRole.ADMIN, true, null, null, null);
    }
}
