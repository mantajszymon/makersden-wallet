package com.sm.makersden.core.repository;

import com.sm.makersden.support.ConcurrentTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyKeyStoreTest {

    private final InMemoryIdempotencyKeyStore store = new InMemoryIdempotencyKeyStore();

    @Test
    void runsTheActionExactlyOnceForANewKey() {
        // given
        AtomicInteger runCount = new AtomicInteger();

        // when
        store.executeOnce("key-1", runCount::incrementAndGet);

        // then
        assertThat(runCount).hasValue(1);
    }

    @Test
    void repeatingTheSameKeyDoesNotRunTheActionAgain() {
        // given
        AtomicInteger runCount = new AtomicInteger();
        store.executeOnce("key-1", runCount::incrementAndGet);

        // when
        store.executeOnce("key-1", runCount::incrementAndGet);

        // then
        assertThat(runCount).hasValue(1);
    }

    @Test
    void differentKeysRunIndependently() {
        // given
        AtomicInteger firstKeyRunCount = new AtomicInteger();
        AtomicInteger secondKeyRunCount = new AtomicInteger();

        // when
        store.executeOnce("key-1", firstKeyRunCount::incrementAndGet);
        store.executeOnce("key-2", secondKeyRunCount::incrementAndGet);

        // then
        assertThat(firstKeyRunCount).hasValue(1);
        assertThat(secondKeyRunCount).hasValue(1);
    }

    @Test
    void aThrownExceptionPropagatesToTheCallerThatOwnsTheKey() {
        // given
        RuntimeException failure = new RuntimeException("boom");

        // when / then
        assertThatThrownBy(() -> store.executeOnce("key-1", () -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void aCachedFailureIsRethrownOnReplayWithoutRerunningTheAction() {
        // given
        RuntimeException failure = new RuntimeException("boom");
        AtomicInteger runCount = new AtomicInteger();
        assertThatThrownBy(() -> store.executeOnce("key-1", () -> {
            runCount.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        // when
        Throwable replayedFailure = catchThrowable(() -> store.executeOnce("key-1", runCount::incrementAndGet));

        // then
        assertThat(replayedFailure).isSameAs(failure);
        assertThat(runCount).hasValue(1);
    }

    @Test
    void concurrentCallsWithTheSameKeyRunTheActionExactlyOnce() {
        // given
        AtomicInteger runCount = new AtomicInteger();
        int concurrentCallers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCallers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when
        try {
            for (int callerIndex = 0; callerIndex < concurrentCallers; callerIndex++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    store.executeOnce("shared-key", runCount::incrementAndGet);
                    return null;
                }));
            }
            startGate.countDown();
            ConcurrentTestSupport.awaitAll(futures);
        } finally {
            executor.shutdown();
        }

        // then
        assertThat(runCount).hasValue(1);
    }

    @Test
    void anErrorPropagatesAndIsReplayedRatherThanLeavingTheKeyStuckForever() {
        // given
        Error failure = new StackOverflowError("simulated");
        AtomicInteger runCount = new AtomicInteger();

        // when
        Throwable originalOutcome = catchThrowable(() -> store.executeOnce("key-1", () -> {
            runCount.incrementAndGet();
            throw failure;
        }));
        Throwable replayedOutcome = catchThrowable(() -> store.executeOnce("key-1", runCount::incrementAndGet));

        // then
        assertThat(originalOutcome).isSameAs(failure);
        assertThat(replayedOutcome).isSameAs(failure);
        assertThat(runCount).hasValue(1);
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
