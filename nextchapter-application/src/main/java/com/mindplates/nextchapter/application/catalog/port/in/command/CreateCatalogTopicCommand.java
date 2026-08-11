package com.mindplates.nextchapter.application.catalog.port.in.command;

public record CreateCatalogTopicCommand(Long fieldId, String slug, String name, String description, int sortOrder) {}
