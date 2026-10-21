package ch.admin.bit.jeap.messagecontract.domain.renovate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Minimal time-to-live cache. Concurrent requests for the same missing key wait for a single load instead of each
 * running it, so a burst of identical requests results in one load. A failed load is not cached: the failure is
 * passed to the requests waiting for it, and the next request loads again. A non-positive time to live disables
 * caching.
 */
final class ExpiringCache<K, V> {

    private final Duration timeToLive;
    private final Clock clock;
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    ExpiringCache(Duration timeToLive, Clock clock) {
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    V get(K key, Supplier<V> loader) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            return loader.get();
        }
        Instant now = clock.instant();
        Entry<V> newEntry = new Entry<>(new CompletableFuture<>(), now.plus(timeToLive));
        Entry<V> entry = entries.compute(key, (k, existing) ->
                existing != null && existing.isValidAt(now) ? existing : newEntry);
        if (entry != newEntry) {
            return await(entry.value());
        }
        entries.values().removeIf(cached -> !cached.isValidAt(now) && cached.value().isDone());
        try {
            V value = loader.get();
            newEntry.value().complete(value);
            return value;
        } catch (RuntimeException | Error ex) {
            entries.remove(key, newEntry);
            newEntry.value().completeExceptionally(ex);
            throw ex;
        }
    }

    private static <V> V await(CompletableFuture<V> value) {
        try {
            return value.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private record Entry<V>(CompletableFuture<V> value, Instant expiresAt) {
        boolean isValidAt(Instant instant) {
            return instant.isBefore(expiresAt);
        }
    }
}
