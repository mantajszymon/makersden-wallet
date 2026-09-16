package com.sm.makersden.core.exception;

/**
 * Thrown when a transfer's source and destination account are the same.
 */
public class SelfTransferException extends RuntimeException {

    public SelfTransferException(String accountId) {
        super(String.format("cannot transfer from account %s to itself", accountId));
    }
}
