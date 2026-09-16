package com.sm.makersden.core.repository;

import com.sm.makersden.core.exception.DuplicateAccountException;
import com.sm.makersden.core.model.Account;

import java.util.Optional;

public interface AccountRepository {

    /**
     * Persists a new account.
     *
     * @throws DuplicateAccountException if an account with this id already exists.
     */
    void create(Account account);

    /**
     * @return the account with this id, or empty if none exists.
     */
    Optional<Account> findById(String id);
}
