package com.passbook.model;

import java.time.LocalDateTime;

public record AuditEntry(long id, String actor, String action, String detail, LocalDateTime createdAt) {
}
