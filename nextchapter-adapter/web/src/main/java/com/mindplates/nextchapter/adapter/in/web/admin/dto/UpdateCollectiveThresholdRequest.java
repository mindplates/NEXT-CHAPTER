package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 집단 루프 임계치 변경 요청. 예산 상한과 달리 무제한이 없다 — 항상 세 값을 함께 보낸다. */
public record UpdateCollectiveThresholdRequest(
        @NotNull @Min(value = 1, message = "질문 임계치는 1 이상이어야 합니다.") Integer questionThreshold,
        @NotNull @Min(value = 1, message = "최소 시도 횟수는 1 이상이어야 합니다.") Integer minAttempts,
        @NotNull
                @Min(value = 1, message = "오답률 임계치는 1 이상이어야 합니다.")
                @Max(value = 100, message = "오답률 임계치는 100 이하여야 합니다.")
                Integer wrongRatePercent) {}
