package com.mindplates.nextchapter.application.generation.port.out;

import java.util.List;

public record EmbeddingRequest(String model, String apiKey, List<String> inputs) {}
