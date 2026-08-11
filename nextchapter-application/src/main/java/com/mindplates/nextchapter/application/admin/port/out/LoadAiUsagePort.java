package com.mindplates.nextchapter.application.admin.port.out;

import java.time.LocalDate;

public interface LoadAiUsagePort {

    /** 이 뼈대가 지금까지 쓴 토큰. 뼈대당 상한 판정의 기준이다. */
    long totalTokensBySkeletonId(Long skeletonId);

    /** 그날 전체가 쓴 토큰. 뼈대에 귀속되지 않는 호출도 포함한다. */
    long totalTokensOn(LocalDate usageDate);
}
