package com.mindplates.nextchapter.common;

import java.util.List;

public record PagedResult<T>(List<T> items, long total) {}
