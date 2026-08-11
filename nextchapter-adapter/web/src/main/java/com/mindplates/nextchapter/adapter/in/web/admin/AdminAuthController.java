package com.mindplates.nextchapter.adapter.in.web.admin;

import com.mindplates.nextchapter.adapter.in.web.admin.dto.AdminAccountResponse;
import com.mindplates.nextchapter.adapter.in.web.admin.dto.AdminLoginRequest;
import com.mindplates.nextchapter.adapter.in.web.admin.dto.AdminTokenResponse;
import com.mindplates.nextchapter.application.admin.port.in.AdminLoginUseCase;
import com.mindplates.nextchapter.application.admin.port.in.GetAdminAccountUseCase;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminLoginUseCase adminLoginUseCase;
    private final GetAdminAccountUseCase getAdminAccountUseCase;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminTokenResponse>> login(@RequestBody @Valid AdminLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(AdminTokenResponse.from(adminLoginUseCase.login(request.toCommand()))));
    }

    /**
     * 토큰 소유자 확인. {@link Principal} 로 받는 이유는 web 어댑터가 security 어댑터에 의존할 수
     * 없기 때문이다 — 어댑터끼리의 의존은 빌드가 막는다. JWT subject 가 이메일이라 이것으로 충분하다.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> me(Principal principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(AdminAccountResponse.from(getAdminAccountUseCase.getByEmail(principal.getName()))));
    }
}
