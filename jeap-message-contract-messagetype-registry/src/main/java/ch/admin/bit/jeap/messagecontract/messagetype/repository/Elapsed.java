package ch.admin.bit.jeap.messagecontract.messagetype.repository;

/**
 * Small helper for timing log statements. Use via static import:
 * <pre>
 * import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;
 * ...
 * long startNanos = System.nanoTime();
 * ...
 * log.info("done in {} ms", elapsedMs(startNanos));
 * </pre>
 */
public final class Elapsed {

    private Elapsed() {
    }

    /**
     * Returns the milliseconds elapsed since {@code startNanos} (captured via {@link System#nanoTime()}).
     */
    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
