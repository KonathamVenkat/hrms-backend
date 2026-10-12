package com.hrms.employee.config;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Builds page requests from client-supplied numbers: never negative, never more than {@link #MAX_SIZE} rows. */
public final class PageLimits {

    public static final int MAX_SIZE = 100;

    private PageLimits() {}

    public static PageRequest of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clamp(size));
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clamp(size), sort);
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
