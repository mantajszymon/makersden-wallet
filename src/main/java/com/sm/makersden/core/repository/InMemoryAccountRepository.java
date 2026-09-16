package com.sm.makersden.core.repository;

import com.sm.makersden.core.exception.DuplicateAccountException;
import com.sm.makersden.core.model.Account;
import lombok.NonNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>{@code create} is atomic with respect to concurrent creates of the same id:
 * exactly one caller succeeds, the other gets {@link DuplicateAccountException}.
 */
public final class InMemoryAccountRepository implements AccountRepository {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void create(@NonNull Account account) {
        Account existing = accounts.putIfAbsent(account.getId(), account);
        if (existing != null) {
            throw new DuplicateAccountException(account.getId());
        }
    }

    @Override
    public Optional<Account> findById(@NonNull String id) {
        return Optional.ofNullable(accounts.get(id));
    }
}
