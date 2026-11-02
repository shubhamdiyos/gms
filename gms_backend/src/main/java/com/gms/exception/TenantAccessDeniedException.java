package com.gms.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * A request that named a row belonging to another tenant.
 *
 * <p>These were thrown as {@code IllegalArgumentException}, which the global handler answers with
 * 400 — a status that tells the caller its request was malformed rather than that it was refused.
 * The read path already answered 403 for exactly this situation, so the same refusal answered
 * two different ways depending on which endpoint reached it.</p>
 */
public class TenantAccessDeniedException extends AccessDeniedException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
