package com.mindplates.nextchapter.application.admin.view;

/**
 * 예산 설정과 현재 사용량.
 *
 * <p>설정만 보여주면 "상한이 얼마인가"는 알 수 있어도 "지금 얼마나 남았나"는 알 수 없다. 상한을 조정할지
 * 판단하려면 둘이 함께 있어야 한다.
 *
 * @param perSkeletonTokenLimit null 이면 무제한
 * @param dailyTokenLimit null 이면 무제한
 * @param dailyUsedTokens 오늘(KST) 사용량
 */
public record AiBudgetView(Long perSkeletonTokenLimit, Long dailyTokenLimit, long dailyUsedTokens, String updatedBy) {}
