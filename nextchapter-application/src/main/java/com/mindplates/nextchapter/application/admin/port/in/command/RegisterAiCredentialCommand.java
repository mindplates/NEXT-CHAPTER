package com.mindplates.nextchapter.application.admin.port.in.command;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

/** 평문 키는 이 커맨드에서만 존재하고, 서비스가 즉시 암호화한다. */
public record RegisterAiCredentialCommand(AiVendor vendor, String apiKey) {}
