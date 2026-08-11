package com.mindplates.nextchapter.application.catalog.port.in.command;

public record CreateCatalogDomainCommand(String slug, String name, String description, int sortOrder) {}
