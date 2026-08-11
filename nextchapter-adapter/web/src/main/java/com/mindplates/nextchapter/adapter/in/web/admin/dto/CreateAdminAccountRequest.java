package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.application.admin.port.in.command.CreateAdminAccountCommand;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminAccountRequest(
        @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 120) String name,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        AdminRole role) {

    public CreateAdminAccountCommand toCommand() {
        return new CreateAdminAccountCommand(email, name, password, role == null ? AdminRole.ADMIN : role);
    }
}
