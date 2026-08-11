package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import java.util.List;

public interface LoadOutlineFindingPort {

    List<OutlineFinding> findBySkeletonId(Long skeletonId);
}
