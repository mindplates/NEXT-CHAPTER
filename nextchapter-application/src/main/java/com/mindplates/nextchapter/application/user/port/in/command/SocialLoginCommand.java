package com.mindplates.nextchapter.application.user.port.in.command;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;

public record SocialLoginCommand(SocialProvider provider, String authorizationCode, String redirectUri) {}
