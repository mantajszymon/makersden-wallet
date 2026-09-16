package com.sm.makersden.api.dto;

import java.math.BigDecimal;

/** Request body for {@code POST /accounts}. */
public record CreateAccountRequest(BigDecimal openingBalance) {
}
