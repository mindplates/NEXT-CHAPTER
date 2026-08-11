package com.mindplates.nextchapter.application.generation.port.out;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;

public interface EmbeddingClientPort {

    AiVendor vendor();

    EmbeddingResult embed(EmbeddingRequest request);
}
