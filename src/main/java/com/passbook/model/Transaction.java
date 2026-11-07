package com.passbook.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One immutable ledger entry. */
public record Transaction(long id, String accountNo, TransactionType type, BigDecimal amount,
                          BigDecimal balanceAfter, String note, LocalDateTime createdAt) {
}
