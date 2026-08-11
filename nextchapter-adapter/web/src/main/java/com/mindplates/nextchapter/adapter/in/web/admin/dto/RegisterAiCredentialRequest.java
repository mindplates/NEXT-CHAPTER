package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.application.admin.port.in.command.RegisterAiCredentialCommand;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 평문 API 키가 들어오는 유일한 지점.
 *
 * <p>대응하는 응답 DTO 가 없다 — 등록 결과는 {@code AiCredentialView}(벤더 · 뒤 4자)로만 나간다.
 * 키를 돌려주는 엔드포인트가 하나라도 있으면 로그·프록시 캐시·브라우저 히스토리에 남는다.
 */
public record RegisterAiCredentialRequest(
        @NotNull(message = "벤더는 필수입니다.") AiVendor vendor, @NotBlank(message = "API 키는 필수입니다.") String apiKey) {

    public RegisterAiCredentialCommand toCommand() {
        return new RegisterAiCredentialCommand(vendor, apiKey);
    }
}
