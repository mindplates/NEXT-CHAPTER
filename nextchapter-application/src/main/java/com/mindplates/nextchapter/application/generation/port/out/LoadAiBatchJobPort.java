package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import java.util.List;

public interface LoadAiBatchJobPort {

    /** 아직 끝나지 않은 배치. 폴러가 이 목록을 훑는다. */
    List<AiBatchJob> findSubmitted();

    List<AiBatchJob> findBySkeletonId(Long skeletonId);
}
