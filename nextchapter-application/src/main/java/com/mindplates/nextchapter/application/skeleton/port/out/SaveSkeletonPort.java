package com.mindplates.nextchapter.application.skeleton.port.out;

import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;

public interface SaveSkeletonPort {

    Skeleton save(Skeleton skeleton);
}
