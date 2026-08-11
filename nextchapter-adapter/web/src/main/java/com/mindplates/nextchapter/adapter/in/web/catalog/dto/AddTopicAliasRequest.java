package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTopicAliasRequest(@NotBlank(message = "별칭은 필수입니다.") @Size(max = 200) String alias) {}
