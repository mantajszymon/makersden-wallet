package com.sm.makersden.core.repository;

public interface IdempotencyKeyStore {

    /**
     * Runs {@code action} exactly once for {@code key}. A concurrent or later call with
     * the same key does not run {@code action} again: it blocks until the first call
     * finishes (if still running) or returns immediately (if it already finished), then
     * either returns normally or rethrows the exact exception the first call threw.
     */
    void executeOnce(String key, Runnable action);
}
