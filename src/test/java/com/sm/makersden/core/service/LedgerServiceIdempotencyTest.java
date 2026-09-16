package com.sm.makersden.core.service;

import com.sm.makersden.core.exception.InsufficientFundsException;
import com.sm.makersden.core.model.Account;
import com.sm.makersden.core.repository.InMemoryAccountRepository;
import com.sm.makersden.core.repository.InMemoryIdempotencyKeyStore;
import com.sm.makersden.support.ConcurrentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceIdempotencyTest {

    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(new InMemoryAccountRepository(), new InMemoryIdempotencyKeyStore());
    }

    @Test
    void transferWithAnIdempotencyKeyMovesMoneyOnce() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account to = ledger.createAccount(new BigDecimal("10.00"));

        // when
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("25.00"), "key-1");

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("75.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("35.00");
    }

    @Test
    void repeatingTheSameKeyReplaysTheOutcomeWithoutTransferringAgain() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account to = ledger.createAccount(new BigDecimal("10.00"));
        String idempotencyKey = "key-1";
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("25.00"), idempotencyKey);

        // when
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("25.00"), idempotencyKey);

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("75.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("35.00");
    }

    @Test
    void repeatingTheSameKeyWithADifferentPayloadStillReplaysTheOriginalOutcome() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account originalDestination = ledger.createAccount(new BigDecimal("10.00"));
        Account differentDestination = ledger.createAccount(BigDecimal.ZERO);
        String idempotencyKey = "key-1";
        ledger.transfer(from.getId(), originalDestination.getId(), new BigDecimal("25.00"), idempotencyKey);

        // when: same key, different destination and amount
        ledger.transfer(from.getId(), differentDestination.getId(), new BigDecimal("50.00"), idempotencyKey);

        // then: the second call's payload is ignored — only the original transfer applied
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("75.00");
        assertThat(ledger.getBalance(originalDestination.getId())).isEqualByComparingTo("35.00");
        assertThat(ledger.getBalance(differentDestination.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    void aFailedOutcomeIsCachedAndReplayedOnRetryEvenIfFundsAreToppedUpAfterwards() {
        // given
        Account from = ledger.createAccount(new BigDecimal("5.00"));
        Account to = ledger.createAccount(BigDecimal.ZERO);
        Account funder = ledger.createAccount(new BigDecimal("100.00"));
        String idempotencyKey = "key-1";

        assertThatThrownBy(() -> ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.00"), idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class);

        // when: from is topped up so a fresh attempt would now succeed, then the same key is retried
        ledger.transfer(funder.getId(), from.getId(), new BigDecimal("50.00"));
        assertThatThrownBy(() -> ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.00"), idempotencyKey))
                .isInstanceOf(InsufficientFundsException.class);

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("55.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    void differentIdempotencyKeysApplyIndependently() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account to = ledger.createAccount(BigDecimal.ZERO);

        // when
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.00"), "key-1");
        ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.00"), "key-2");

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("80.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("20.00");
    }

    @Test
    void concurrentRetriesForTheSameKeyApplyTheTransferExactlyOnce() {
        // given
        Account from = ledger.createAccount(new BigDecimal("100.00"));
        Account to = ledger.createAccount(BigDecimal.ZERO);
        String sharedIdempotencyKey = "shared-key";
        int concurrentRetries = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRetries);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when
        try {
            for (int retryIndex = 0; retryIndex < concurrentRetries; retryIndex++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    ledger.transfer(from.getId(), to.getId(), new BigDecimal("10.00"), sharedIdempotencyKey);
                    return null;
                }));
            }
            startGate.countDown();
            ConcurrentTestSupport.awaitAll(futures);
        } finally {
            executor.shutdown();
        }

        // then
        assertThat(ledger.getBalance(from.getId())).isEqualByComparingTo("90.00");
        assertThat(ledger.getBalance(to.getId())).isEqualByComparingTo("10.00");
    }
}
