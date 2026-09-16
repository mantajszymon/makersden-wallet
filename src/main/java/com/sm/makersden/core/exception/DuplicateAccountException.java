package com.sm.makersden.core.exception;

/**
 * Thrown by {@code AccountRepository.create} when an account with the given id
 * already exists.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountId) {
        super(String.format("an account with id %s already exists", accountId));
    }
}
