package com.sm.makersden.core.model;

import com.sm.makersden.core.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the concurrency guarantees {@link Account#transfer} makes under heavy,
 * genuinely concurrent load: no lost updates, balances never go negative, and
 * transfers on disjoint accounts are not serialized behind one another.
 */
class AccountConcurrencyTest {

    private static final BigDecimal STANDARD_OPENING_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal STANDARD_TRANSFER_AMOUNT = new BigDecimal("10.00");

    @Test
    void manyConcurrentTransfersAcrossASharedPoolOfAccountsConserveTheTotalBalance() {
        // given
        int accountCount = 8;
        BigDecimal openingBalancePerAccount = new BigDecimal("1000.00");
        List<Account> accountPool = createAccountPool(accountCount, openingBalancePerAccount);
        BigDecimal totalOpeningBalance = openingBalancePerAccount.multiply(BigDecimal.valueOf(accountCount));

        int concurrentWorkers = 16;
        int transfersPerWorker = 500;

        // when
        runConcurrently(concurrentWorkers, concurrentWorkers,
                () -> transferBetweenRandomAccountsRepeatedly(accountPool, transfersPerWorker));

        // then
        assertThat(sumOfBalances(accountPool)).isEqualByComparingTo(totalOpeningBalance);
        assertThat(accountPool).allSatisfy(account -> assertThat(account.getBalance()).isNotNegative());
    }

    @Test
    void balanceNeverGoesNegativeWhenConcurrentWithdrawalsExceedTheAvailableBalance() {
        // given
        Account sourceAccount = new Account("source", STANDARD_OPENING_BALANCE);
        Account sinkAccount = new Account("sink", BigDecimal.ZERO);
        BigDecimal withdrawalAmount = new BigDecimal("1.00");
        int withdrawalAttempts = 300;
        int workerPoolSize = 50;
        int expectedSuccessfulWithdrawals = STANDARD_OPENING_BALANCE.divide(withdrawalAmount).intValueExact();

        // when
        List<Boolean> withdrawalOutcomes = runConcurrently(withdrawalAttempts, workerPoolSize,
                () -> attemptTransferIgnoringInsufficientFunds(sourceAccount, sinkAccount, withdrawalAmount));

        // then
        long successfulWithdrawals = withdrawalOutcomes.stream().filter(Boolean::booleanValue).count();
        assertThat(successfulWithdrawals).isEqualTo(expectedSuccessfulWithdrawals);
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sinkAccount.getBalance()).isEqualByComparingTo(STANDARD_OPENING_BALANCE);
    }

    @Test
    void transfersOnDisjointAccountsProceedWithoutWaitingOnEachOther() throws Exception {
        // given
        Account accountWithLockHeld = new Account("locked", STANDARD_OPENING_BALANCE);
        Account unrelatedSourceAccount = new Account("unrelated-source", STANDARD_OPENING_BALANCE);
        Account unrelatedDestinationAccount = new Account("unrelated-destination", STANDARD_OPENING_BALANCE);
        ReentrantLock heldLock = internalLockOf(accountWithLockHeld);

        heldLock.lock();
        try {
            // when
            CompletableFuture<Void> unrelatedTransfer = transferAsync(
                    unrelatedSourceAccount, unrelatedDestinationAccount);
            unrelatedTransfer.get(1, TimeUnit.SECONDS);

            // then
            assertThat(unrelatedSourceAccount.getBalance()).isEqualByComparingTo("90.00");
            assertThat(unrelatedDestinationAccount.getBalance()).isEqualByComparingTo("110.00");
        } finally {
            heldLock.unlock();
        }
    }

    private static List<Account> createAccountPool(int accountCount, BigDecimal openingBalance) {
        List<Account> accountPool = new ArrayList<>();
        for (int accountIndex = 0; accountIndex < accountCount; accountIndex++) {
            accountPool.add(new Account("acc-" + accountIndex, openingBalance));
        }
        return accountPool;
    }

    private static void transferBetweenRandomAccountsRepeatedly(List<Account> accountPool, int transferCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int transferIndex = 0; transferIndex < transferCount; transferIndex++) {
            transferBetweenRandomDistinctAccounts(accountPool, random);
        }
    }

    private static void transferBetweenRandomDistinctAccounts(List<Account> accountPool, ThreadLocalRandom random) {
        int fromIndex = random.nextInt(accountPool.size());
        int toIndex;
        do {
            toIndex = random.nextInt(accountPool.size());
        } while (toIndex == fromIndex);

        attemptTransferIgnoringInsufficientFunds(
                accountPool.get(fromIndex), accountPool.get(toIndex), randomSmallAmount(random));
    }

    private static BigDecimal randomSmallAmount(ThreadLocalRandom random) {
        int minWholeDollars = 1;
        int maxWholeDollarsInclusive = 5;
        return BigDecimal.valueOf(random.nextInt(minWholeDollars, maxWholeDollarsInclusive + 1));
    }

    private static boolean attemptTransferIgnoringInsufficientFunds(Account from, Account to, BigDecimal amount) {
        try {
            Account.transfer(from, to, amount);
            return true;
        } catch (InsufficientFundsException expected) {
            return false;
        }
    }

    private static BigDecimal sumOfBalances(List<Account> accounts) {
        return accounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static CompletableFuture<Void> transferAsync(Account from, Account to) {
        return CompletableFuture.runAsync(() -> Account.transfer(from, to, AccountConcurrencyTest.STANDARD_TRANSFER_AMOUNT));
    }

    /** Void-returning convenience for {@link #runConcurrently(int, int, InterruptibleTask)}. */
    private static void runConcurrently(int taskCount, int workerPoolSize, InterruptibleVoidTask task) {
        runConcurrently(taskCount, workerPoolSize, (InterruptibleTask<Void>) () -> {
            task.run();
            return null;
        });
    }

    /**
     * Runs {@code taskCount} copies of {@code task} on a pool of {@code workerPoolSize}
     * threads, released together through a start gate so they genuinely overlap, and
     * returns each task's result in submission order.
     */
    private static <T> List<T> runConcurrently(int taskCount, int workerPoolSize, InterruptibleTask<T> task) {
        ExecutorService executor = Executors.newFixedThreadPool(workerPoolSize);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }

            startGate.countDown();
            return futures.stream().map(AccountConcurrencyTest::resultOf).toList();
        } finally {
            executor.shutdown();
        }
    }

    private static <T> T resultOf(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static ReentrantLock internalLockOf(Account account) {
        try {
            Field lockField = Account.class.getDeclaredField("lock");
            lockField.setAccessible(true);
            return (ReentrantLock) lockField.get(account);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface InterruptibleTask<T> {
        T call() throws InterruptedException;
    }

    @FunctionalInterface
    private interface InterruptibleVoidTask {
        void run() throws InterruptedException;
    }
}
