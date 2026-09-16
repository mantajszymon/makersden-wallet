package com.sm.makersden.core.exception;

import java.math.BigDecimal;

/**
 * Thrown when a transfer would take an account's balance below zero.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
        super(String.format("account %s has insufficient funds: requested %s, available %s",
                accountId, requested, available));
    }
}
