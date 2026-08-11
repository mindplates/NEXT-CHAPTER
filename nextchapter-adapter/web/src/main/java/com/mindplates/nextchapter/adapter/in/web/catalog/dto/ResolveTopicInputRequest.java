package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveTopicInputRequest(@NotBlank(message = "주제 입력은 필수입니다.") @Size(max = 500) String input) {}
