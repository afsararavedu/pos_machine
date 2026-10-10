package com.aifsar.pos.auth.domain;

/**
 * ADMIN    - everything, including user management, reversals and batch close.
 * AUDITOR  - read-only access to all reports.
 * MERCHANT - capture/void and reports for its own merchant only.
 * TERMINAL - a POS device; can only start payments as itself.
 */
public enum Role { ADMIN, AUDITOR, MERCHANT, TERMINAL }
