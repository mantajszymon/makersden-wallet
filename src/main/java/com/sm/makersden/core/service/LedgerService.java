package com.sm.makersden.core.service;

import com.sm.makersden.core.exception.AccountNotFoundException;
import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.InvalidAmountException;
import com.sm.makersden.core.exception.SelfTransferException;
import com.sm.makersden.core.model.Account;
import com.sm.makersden.core.repository.AccountRepository;
import com.sm.makersden.core.repository.IdempotencyKeyStore;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public final class LedgerService {

    @NonNull
    private final AccountRepository accountRepository;
    @NonNull
    private final IdempotencyKeyStore idempotencyKeyStore;

    private final AtomicLong nextAccountId = new AtomicLong(1);

    /**
     * Creates a new account with a server-generated id.
     *
     * @param openingBalance starting balance; must not be negative
     * @throws InvalidAmountException if openingBalance is invalid (see {@link Account})
     */
    public Account createAccount(BigDecimal openingBalance) {
        String id = String.valueOf(nextAccountId.getAndIncrement());
        Account account = new Account(id, openingBalance);
        accountRepository.create(account);
        return account;
    }

    /**
     * @throws AccountNotFoundException if no account exists with this id.
     */
    public BigDecimal getBalance(@NonNull String accountId) {
        return findAccountOrThrow(accountId).getBalance();
    }

    /**
     * Moves {@code amount} from the account {@code fromAccountId} to the account
     * {@code toAccountId}, atomically.
     *
     * @throws SelfTransferException      if fromAccountId equals toAccountId.
     * @throws AccountNotFoundException   if either account does not exist.
     * @throws InvalidAmountException     if amount is not a positive amount with at
     *                                    most 2 decimal places.
     * @throws InsufficientFundsException if the source account's balance is less
     *                                    than amount.
     */
    public void transfer(@NonNull String fromAccountId, @NonNull String toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new SelfTransferException(fromAccountId);
        }
        Account from = findAccountOrThrow(fromAccountId);
        Account to = findAccountOrThrow(toAccountId);
        Account.transfer(from, to, amount);
    }

    /**
     * Same as {@link #transfer(String, String, BigDecimal)}, but applied at most once per
     * {@code idempotencyKey}: a repeated key returns the original outcome (success or the
     * same exception) without transferring again, even if a retry arrives while the
     * original request for that key is still being processed.
     *
     * @throws SelfTransferException      if fromAccountId equals toAccountId.
     * @throws AccountNotFoundException   if either account does not exist.
     * @throws InvalidAmountException     if amount is not a positive amount with at
     *                                    most 2 decimal places.
     * @throws InsufficientFundsException if the source account's balance is less
     *                                    than amount.
     */
    public void transfer(@NonNull String fromAccountId, @NonNull String toAccountId, BigDecimal amount,
                          @NonNull String idempotencyKey) {
        idempotencyKeyStore.executeOnce(idempotencyKey, () -> transfer(fromAccountId, toAccountId, amount));
    }

    private Account findAccountOrThrow(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
