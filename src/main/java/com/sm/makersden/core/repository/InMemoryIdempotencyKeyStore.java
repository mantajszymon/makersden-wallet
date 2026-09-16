package com.sm.makersden.core.repository;

import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The first caller for a key claims it atomically via {@code putIfAbsent} and runs
 * {@code action}; every other caller for that key — concurrent or later — joins the
 * same future instead of running {@code action} again, blocking until it completes if
 * necessary. Completed entries are kept forever: there is no eviction or TTL.
 */
public final class InMemoryIdempotencyKeyStore implements IdempotencyKeyStore {

    private final ConcurrentHashMap<String, CompletableFuture<Void>> outcomes = new ConcurrentHashMap<>();

    @Override
    public void executeOnce(@NonNull String key, @NonNull Runnable action) {
        CompletableFuture<Void> ownFuture = new CompletableFuture<>();
        CompletableFuture<Void> existingFuture = outcomes.putIfAbsent(key, ownFuture);

        if (existingFuture == null) {
            runAndComplete(action, ownFuture);
        } else {
            joinExisting(existingFuture);
        }
    }

    private static void runAndComplete(Runnable action, CompletableFuture<Void> ownFuture) {
        try {
            action.run();
            ownFuture.complete(null);
        } catch (RuntimeException | Error e) {
            ownFuture.completeExceptionally(e);
            throw e;
        }
    }

    private static void joinExisting(CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException original) {
                throw original;
            }

            if (cause instanceof Error original) {
                throw original;
            }
            throw e;
        }
    }
}
