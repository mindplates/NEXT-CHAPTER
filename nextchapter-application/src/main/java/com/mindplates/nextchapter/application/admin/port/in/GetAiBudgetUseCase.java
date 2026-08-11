package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.view.AiBudgetView;

public interface GetAiBudgetUseCase {

    AiBudgetView current();

    /** 이 뼈대가 지금까지 쓴 토큰. 검수 화면이 "이 뼈대가 얼마를 썼나"를 보여준다. */
    long usedTokensBySkeleton(Long skeletonId);
}
