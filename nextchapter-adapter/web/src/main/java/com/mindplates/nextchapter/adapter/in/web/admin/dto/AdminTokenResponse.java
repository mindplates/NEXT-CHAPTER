package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import java.time.Instant;

public record AdminTokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    public static AdminTokenResponse from(AdminTokenView view) {
        return new AdminTokenResponse(view.accessToken(), view.tokenType(), view.expiresAt());
    }
}
