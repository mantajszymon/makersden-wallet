package com.sm.makersden.core.exception;

/**
 * Thrown when an operation refers to an account id that does not exist.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super(String.format("no account with id %s", accountId));
    }
}
