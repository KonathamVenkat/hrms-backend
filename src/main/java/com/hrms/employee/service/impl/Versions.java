package com.hrms.employee.service.impl;

import org.springframework.dao.OptimisticLockingFailureException;

/** Rejects an edit made from a screen that has since been overtaken by someone else's save. */
final class Versions {

    private Versions() {}

    /**
     * @param sent    version the client read (null from an older client: not checked)
     * @param current version stored now
     * @throws OptimisticLockingFailureException (reported as HTTP 409) when they differ
     */
    static void requireCurrent(Long sent, Long current) {
        if (sent != null && !sent.equals(current)) {
            throw new OptimisticLockingFailureException(
                "Version " + sent + " is stale, current is " + current);
        }
    }
}
