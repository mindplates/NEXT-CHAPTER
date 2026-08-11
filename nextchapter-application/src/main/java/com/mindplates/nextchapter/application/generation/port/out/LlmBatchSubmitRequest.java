package com.mindplates.nextchapter.application.generation.port.out;

import java.util.List;

public record LlmBatchSubmitRequest(String model, String apiKey, List<LlmBatchItem> items) {}
