package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;

public interface SaveAiBatchJobPort {

    AiBatchJob save(AiBatchJob job);
}
