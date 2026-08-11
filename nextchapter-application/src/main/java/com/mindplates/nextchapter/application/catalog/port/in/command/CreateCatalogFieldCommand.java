package com.mindplates.nextchapter.application.catalog.port.in.command;

public record CreateCatalogFieldCommand(Long domainId, String slug, String name, String description, int sortOrder) {}
