package com.mindplates.nextchapter.adapter.in.web.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmTopicCandidateRequest(@NotNull(message = "고른 주제 ID 는 필수입니다.") Long topicId) {}
