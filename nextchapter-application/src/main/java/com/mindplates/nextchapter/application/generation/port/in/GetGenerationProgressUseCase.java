package com.mindplates.nextchapter.application.generation.port.in;

import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;

public interface GetGenerationProgressUseCase {

    GenerationProgressView bySkeletonId(Long skeletonId);

    GenerationProgressView byTopicId(Long topicId);
}
