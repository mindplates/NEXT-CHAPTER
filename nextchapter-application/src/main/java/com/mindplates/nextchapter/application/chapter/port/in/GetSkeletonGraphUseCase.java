package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.view.SkeletonGraphView;

public interface GetSkeletonGraphUseCase {

    SkeletonGraphView bySkeletonId(Long skeletonId);
}
