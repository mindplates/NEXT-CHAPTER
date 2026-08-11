package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.application.admin.port.in.command.AdminLoginCommand;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "이메일은 필수입니다.") String email, @NotBlank(message = "비밀번호는 필수입니다.") String password) {

    public AdminLoginCommand toCommand() {
        return new AdminLoginCommand(email, password);
    }
}
