package com.sm.makersden.support;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class ConcurrentTestSupport {

    private ConcurrentTestSupport() {
    }

    /**
     * Waits for every future to complete. Rethrows the first task failure unchecked so a bug surfaced by a background
     * thread fails the test instead of being silently swallowed.
     */
    public static void awaitAll(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }
}
