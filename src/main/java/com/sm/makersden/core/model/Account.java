package com.sm.makersden.core.model;

import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.exception.SelfTransferException;
import com.sm.makersden.core.util.MoneyValidation;
import com.sm.makersden.core.util.TransferValidation;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A named holder of money. No currency is tracked; every account is the same
 * implicit currency.
 * Balance reads are lock-free and never block
 */
public final class Account {

    @Getter
    private final String id;
    @Getter
    private volatile BigDecimal balance;
    private final ReentrantLock lock = new ReentrantLock();

    public Account(@NonNull String id, BigDecimal balance) {
        this.id = id;
        this.balance = MoneyValidation.requireValidBalance(balance);
    }

    /**
     * Moves {@code amount} from {@code from} to {@code to}, atomically: either both
     * balances change or neither does. Transfers on disjoint account pairs proceed in
     * parallel.
     *
     * @throws SelfTransferException      if from and to are the same account.
     * @throws com.sm.makersden.core.exception.InvalidAmountException if amount is not
     *                                    a positive amount with at most 2 decimal places.
     * @throws InsufficientFundsException if from's balance is less than amount.
     */
    public static void transfer(@NonNull Account from, @NonNull Account to, BigDecimal amount) throws SelfTransferException, InsufficientFundsException {
        TransferValidation.rejectSelfTransfer(from, to);
        BigDecimal validAmount = MoneyValidation.requireValidAmount(amount);

        try (AccountLocks ignored = AccountLocks.acquire(from, to)) {
            TransferValidation.rejectInsufficientFundsTransfer(from, validAmount);

            from.balance = from.balance.subtract(validAmount);
            to.balance = to.balance.add(validAmount);
        }
    }

    /**
     * Holds both accounts' locks for the lifetime of a transfer. Always acquires them
     * in ascending {@code id} order, which is not fixed across calls — so that two transfers
     * racing over the same pair of accounts (in either direction) request the locks in the same global order and
     * can never deadlock on each other.
     */
    private record AccountLocks(ReentrantLock first, ReentrantLock second) implements AutoCloseable {

        static AccountLocks acquire(Account a, Account b) {
            boolean aFirst = a.id.compareTo(b.id) < 0;
            ReentrantLock first = aFirst ? a.lock : b.lock;
            ReentrantLock second = aFirst ? b.lock : a.lock;
            first.lock();
            second.lock();
            return new AccountLocks(first, second);
        }

        @Override
        public void close() {
            second.unlock();
            first.unlock();
        }
    }
}
