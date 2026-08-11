package com.mindplates.nextchapter.application.admin.port.in;

import com.mindplates.nextchapter.application.admin.view.AiBudgetView;

public interface UpdateAiBudgetUseCase {

    /** null 은 무제한이다. 무제한을 허용하는 것은 설계 결정이다 — 운영자가 필요하면 끌 수 있어야 한다. */
    AiBudgetView update(Long perSkeletonTokenLimit, Long dailyTokenLimit, String actor);
}
