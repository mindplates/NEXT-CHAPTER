package com.mindplates.nextchapter.adapter.in.web.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.user.port.in.GetSupportedSocialProvidersUseCase;
import com.mindplates.nextchapter.application.user.port.in.GetUserUseCase;
import com.mindplates.nextchapter.application.user.port.in.SocialLoginUseCase;
import com.mindplates.nextchapter.application.user.port.in.command.SocialLoginCommand;
import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.application.user.view.UserView;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserAuthController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("사용자 인증 API")
class UserAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SocialLoginUseCase socialLoginUseCase;

    @MockitoBean
    GetUserUseCase getUserUseCase;

    @MockitoBean
    GetSupportedSocialProvidersUseCase getSupportedSocialProvidersUseCase;

    private static UserView view() {
        return new UserView(42L, SocialProvider.GOOGLE, "learner@example.com", "학습자1", null);
    }

    @Test
    @DisplayName("로그인은 토큰과 프로필을 함께 내린다")
    void loginReturnsTokenAndProfile() throws Exception {
        when(socialLoginUseCase.login(any()))
                .thenReturn(new UserTokenView("jwt-token", "Bearer", Instant.parse("2026-08-19T00:00:00Z"), view()));

        mockMvc.perform(
                        post("/api/auth/social/GOOGLE")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"authorizationCode\":\"code-1\",\"redirectUri\":\"http://localhost:34100/login/google\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.id").value(42))
                .andExpect(jsonPath("$.data.user.displayName").value("학습자1"));
    }

    /** 경로의 제공자가 커맨드에 그대로 실려야 한다 — 어긋나면 사용자가 다른 제공자로 로그인된다. */
    @Test
    @DisplayName("경로의 제공자가 커맨드로 넘어간다")
    void pathProviderReachesCommand() throws Exception {
        when(socialLoginUseCase.login(new SocialLoginCommand(SocialProvider.KAKAO, "code-1", "http://localhost/cb")))
                .thenReturn(new UserTokenView("jwt-token", "Bearer", Instant.now(), view()));

        mockMvc.perform(post("/api/auth/social/KAKAO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"code-1\",\"redirectUri\":\"http://localhost/cb\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인가 코드가 없으면 400 이다")
    void rejectsMissingCode() throws Exception {
        mockMvc.perform(post("/api/auth/social/GOOGLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\" \",\"redirectUri\":\"http://localhost/cb\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("설정되지 않은 제공자는 400 이다")
    void rejectsUnsupportedProvider() throws Exception {
        when(socialLoginUseCase.login(any())).thenThrow(new InvalidOperationException("KAKAO 소셜 로그인이 설정되지 않았습니다."));

        mockMvc.perform(post("/api/auth/social/KAKAO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"code-1\",\"redirectUri\":\"http://localhost/cb\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 목록을 설정에서 역산하는 것이 요점이다 — 하드코딩하면 설정 안 된 제공자의 버튼이 화면에 남는다. */
    @Test
    @DisplayName("지원 제공자 목록이 정렬돼 나온다")
    void listsSupportedProviders() throws Exception {
        when(getSupportedSocialProvidersUseCase.supported())
                .thenReturn(Set.of(SocialProvider.KAKAO, SocialProvider.GOOGLE));

        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.data[1]").value("KAKAO"));
    }

    @Test
    @DisplayName("내 프로필은 토큰 subject 의 사용자 ID 로 조회한다")
    void meUsesTokenSubject() throws Exception {
        when(getUserUseCase.getById(42L)).thenReturn(view());

        mockMvc.perform(get("/api/auth/me").principal(principal("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42));
    }

    /** 관리자 토큰이 사용자 API 에 들어오면 subject 가 이메일이다. 500 이 아니라 401 이어야 한다. */
    @Test
    @DisplayName("사용자 토큰이 아니면 401 이다")
    void rejectsNonUserToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").principal(principal("admin@nextchapter.local")))
                .andExpect(status().isUnauthorized());
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
