package com.mindplates.nextchapter.adapter.in.web.admin.dto;

import jakarta.validation.constraints.Min;

/**
 * 예산 상한 변경 요청.
 *
 * <p>{@code null} 은 <b>무제한</b>이다. "값을 안 보냈다"와 "무제한으로 바꿔라"를 구분하지 않는다 —
 * 부분 수정을 허용하면 뼈대당만 바꾸려던 요청이 일일 상한을 건드리지 않았다는 보장을 요청 본문만 보고는
 * 할 수 없고, 무제한으로 바꾸는 경로가 별도 필드를 필요로 해 표현이 둘로 갈린다. 항상 두 값을 함께 보낸다.
 */
public record UpdateAiBudgetRequest(
        @Min(value = 1, message = "뼈대당 상한은 1 이상이거나 무제한(null)이어야 합니다.") Long perSkeletonTokenLimit,
        @Min(value = 1, message = "일일 상한은 1 이상이거나 무제한(null)이어야 합니다.") Long dailyTokenLimit) {}
