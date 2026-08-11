package com.mindplates.nextchapter.adapter.in.web.admin;

import com.mindplates.nextchapter.adapter.in.web.admin.dto.AdminAccountResponse;
import com.mindplates.nextchapter.adapter.in.web.admin.dto.CreateAdminAccountRequest;
import com.mindplates.nextchapter.application.admin.port.in.CreateAdminAccountUseCase;
import com.mindplates.nextchapter.application.admin.port.in.GetAdminAccountUseCase;
import com.mindplates.nextchapter.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountAdminController {

    private final GetAdminAccountUseCase getAdminAccountUseCase;
    private final CreateAdminAccountUseCase createAdminAccountUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAccountResponse>>> list() {
        List<AdminAccountResponse> result = getAdminAccountUseCase.list().stream()
                .map(AdminAccountResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminAccountResponse>> create(
            @RequestBody @Valid CreateAdminAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(AdminAccountResponse.from(createAdminAccountUseCase.create(request.toCommand()))));
    }
}
