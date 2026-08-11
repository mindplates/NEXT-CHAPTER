package com.mindplates.nextchapter.application.generation.port.in;

import com.mindplates.nextchapter.application.generation.view.AiBatchJobView;
import java.util.List;

public interface GetAiBatchJobUseCase {

    List<AiBatchJobView> bySkeletonId(Long skeletonId);
}
