package ch.admin.bit.jeap.messagecontract.domain.renovate;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ExpiringCacheTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T02:59:00Z"), ZoneOffset.UTC);

    @Test
    void concurrentRequestsForSameKeyShareOneLoad() throws Exception {
        ExpiringCache<String, String> cache = new ExpiringCache<>(Duration.ofMinutes(5), clock);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> cache.get("key", () -> {
                loads.incrementAndGet();
                loaderStarted.countDown();
                await(releaseLoader);
                return "value";
            }));
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> cache.get("key", () -> {
                loads.incrementAndGet();
                return "other";
            }));
            releaseLoader.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("value");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("value");
            assertThat(loads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedLoadIsNotCached() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(Duration.ofMinutes(5), clock);

        assertThatIllegalStateException().isThrownBy(() -> cache.get("key", () -> {
            throw new IllegalStateException("failed");
        }));

        assertThat(cache.get("key", () -> "value")).isEqualTo("value");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
