package com.sm.makersden.api.dto;

import java.math.BigDecimal;

/** Response body for {@code POST /accounts} and {@code GET /accounts/{id}}. */
public record AccountResponse(String id, BigDecimal balance) {
}
