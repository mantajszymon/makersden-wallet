package com.sm.makersden.api.dto;

import java.math.BigDecimal;

/** Request body for {@code POST /transfers}. */
public record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount) {
}
