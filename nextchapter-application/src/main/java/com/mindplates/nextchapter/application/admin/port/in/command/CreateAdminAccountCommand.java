package com.mindplates.nextchapter.application.admin.port.in.command;

import com.mindplates.nextchapter.domain.admin.model.AdminRole;

public record CreateAdminAccountCommand(String email, String name, String password, AdminRole role) {}
