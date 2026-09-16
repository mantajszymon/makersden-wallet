package com.sm.makersden.core.exception;

/**
 * Thrown when a monetary amount supplied to the ledger violates one of its invariants
 * — e.g. a negative opening balance, an amount with more than 2 decimal places, or a
 * non-positive transfer amount.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
