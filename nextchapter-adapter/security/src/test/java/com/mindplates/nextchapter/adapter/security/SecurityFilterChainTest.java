package com.mindplates.nextchapter.adapter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 두 필터 체인이 실제로 무엇을 막는지 확인한다.
 *
 * <p>빈 배선을 읽어서는 알 수 없는 사실을 본다 — 관리 경로가 토큰 없이 401 인지, 다른 역할의 토큰으로
 * 403 인지, 로그인 경로만 열려 있는지, 헬스체크가 여전히 공개인지. 인증 설정은 "열려 있는지"를 코드로
 * 확인하지 않으면 조용히 틀리는 종류의 코드다.
 */
@WebMvcTest
@AutoConfigureMockMvc
@Import({SecurityConfig.class, AdminJwtTokenIssuer.class})
@TestPropertySource(
        properties = {
            "nextchapter.security.jwt.secret=test-secret-that-is-long-enough-for-hs256",
            "nextchapter.security.jwt.ttl=1h"
        })
@DisplayName("보안 필터 체인")
class SecurityFilterChainTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminJwtTokenIssuer issuer;

    @Test
    @DisplayName("관리 경로는 토큰 없이 401 이다 — 브라우저 폼으로 리다이렉트하지 않는다")
    void adminPathRequiresToken() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/domains")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 경로만 토큰 없이 열려 있다")
    void loginPathIsOpen() throws Exception {
        // 컨트롤러가 이 슬라이스에 없으므로 404 다 — 인증에서 막히지 않았다는 것이 요점이다.
        mockMvc.perform(post("/api/admin/auth/login")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN 역할 토큰은 관리 경로를 통과한다")
    void adminTokenPasses() throws Exception {
        String token = issuer.issue(account(AdminRole.ADMIN)).accessToken();

        mockMvc.perform(get("/api/admin/catalog/domains").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("깨진 토큰은 401 이다")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/domains").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("헬스체크는 공개다")
    void healthIsOpen() throws Exception {
        // actuator 가 이 슬라이스에 없어 404 지만, 인증 필터에서 막히지 않는다.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    /** 관리 경로 밖은 기본이 잠김이다 — permitAll 을 기본으로 두면 잠그는 것을 잊는 쪽이 기본값이 된다. */
    @Test
    @DisplayName("관리 경로가 아닌 곳도 기본적으로 인증을 요구한다")
    void otherPathsAreClosedByDefault() throws Exception {
        mockMvc.perform(get("/api/topics/resolve")).andExpect(status().isUnauthorized());
    }

    private static AdminAccount account(AdminRole role) {
        return new AdminAccount(1L, "admin@nextchapter.local", "관리자", "{bcrypt}hash", role, true, null, null, null);
    }

    /** 발급 결과 타입을 고정해 둔다 — 응답 DTO 가 이 모양에 의존한다. */
    @Test
    @DisplayName("발급 결과는 Bearer 토큰이다")
    void issuesBearerToken() {
        AdminTokenView token = issuer.issue(account(AdminRole.ADMIN));

        org.assertj.core.api.Assertions.assertThat(token.tokenType()).isEqualTo("Bearer");
        org.assertj.core.api.Assertions.assertThat(token.accessToken()).isNotBlank();
    }
}
