package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubAppCredentialsProvider;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.TagOpt;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;

/**
 * Maintains a local bare mirror clone for every URI listed under {@code messages.repositories[*]} so that
 * {@link MessageTypeRepository} can serve per-request worktrees without going back to the real upstream.
 * <p>
 * <b>Layout.</b> Each configured repository is cached at
 * {@code <messages.repository-cache.directory>/<sha256(uri)>/} as a {@code git clone --mirror --bare}.
 * {@link #getCacheRepoDir(String)} returns the bare repo directory; callers hand it to
 * {@link MessageTypeRepository#setReferenceCacheDir(File)}, which writes the {@code objects} subdirectory
 * into {@code .git/objects/info/alternates} and reads refs directly from the cache's on-disk database.
 * <p>
 * <b>Lifecycle.</b> {@link #refreshAll()} is invoked by
 * {@link MessageTypeRepositoryReferenceCacheRefresher} on application startup (because the container
 * filesystem is ephemeral) and on a configurable cron (default {@code 0 0 1 * * *}). Concurrent invocations
 * are serialised via {@link ReentrantLock} - {@link #refreshAll()} uses non-blocking
 * {@link ReentrantLock#tryLock() tryLock} so a scheduled tick overlapping the startup refresh is dropped,
 * while {@link #refreshOne(String)} uses blocking {@link ReentrantLock#lock() lock} so callers waiting on
 * a freshening side pick up the newly-fetched refs.
 * <p>
 * <b>Refresh semantics.</b> For each configured repository:
 * <ul>
 *   <li>If the cache directory passes the bare-repo sanity check (HEAD / objects / config present), a
 *       mirror-style fetch is run against it ({@code setRemoveDeletedRefs(true)} and
 *       {@code TagOpt.FETCH_TAGS}) so the cache tracks server-side deletions and tag updates.</li>
 *   <li>If the directory is missing or invalid, any leftover contents are removed and a fresh
 *       {@code clone --bare --mirror} is performed.</li>
 *   <li>Failures for a single repository are logged and swallowed - the cache is best-effort and a stale
 *       or missing entry just falls back to the existing direct-clone path in {@link MessageTypeRepository}.</li>
 * </ul>
 * <p>
 * <b>Stale-cache handling.</b> {@link #refreshOne(String)} is called both reactively
 * ({@link MessageTypeRepository#checkoutAt} on fetch/checkout failure for an unknown commit/branch) and
 * eagerly (before resolving a branch-only contract upload, so a moved branch tip is picked up).
 * {@code refreshOne} debounces calls per URI via
 * {@link MessageTypeRepositoryCacheProperties#getRefreshDebounceMillis()}: a batch of uploads pays at
 * most one upstream round-trip even though every branch request triggers it.
 * <p>
 * GitHub-typed repositories use the same {@link GitHubAppCredentialsProvider} as the per-request clone path,
 * sharing the configured {@link MeterRegistry}.
 */
@Slf4j
@Component
public class MessageTypeRepositoryReferenceCache {

    private final MessageTypeRepositoryCacheProperties cacheProperties;
    private final MessageTypeRepositoryProperties repositoryProperties;
    private final MeterRegistry meterRegistry;
    private final ReentrantLock refreshLock = new ReentrantLock();
    /**
     * Tracks the {@link System#nanoTime()} at which the last successful refresh completed for each URI.
     * Used by {@link #refreshOne(String)} to debounce concurrent eager refreshes.
     */
    private final ConcurrentHashMap<String, Long> lastRefreshNanos = new ConcurrentHashMap<>();

    public MessageTypeRepositoryReferenceCache(MessageTypeRepositoryCacheProperties cacheProperties,
                                               MessageTypeRepositoryProperties repositoryProperties,
                                               MeterRegistry meterRegistry) {
        this.cacheProperties = cacheProperties;
        this.repositoryProperties = repositoryProperties;
        this.meterRegistry = meterRegistry;
    }

    public boolean isEnabled() {
        return cacheProperties.isEnabled();
    }

    /**
     * Returns the cached bare repository directory for {@code gitUri}, if the cache is enabled and the
     * entry passes the bare-repo sanity check. Callers (typically {@link MessageTypeRepositoryFactory})
     * hand this directory to {@link MessageTypeRepository#setReferenceCacheDir(File)}; the per-request
     * worktree reads refs and objects from it via JGit's on-disk APIs without opening a transport.
     * <p>
     * Returns {@link Optional#empty()} for unknown URIs, missing entries, partially-deleted entries, and
     * when the cache is disabled - any of those simply route the request through the existing direct-clone
     * path.
     */
    public Optional<File> getCacheRepoDir(String gitUri) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        File cacheDir = cacheDirFor(gitUri);
        if (!isValidBareRepo(cacheDir)) {
            return Optional.empty();
        }
        return Optional.of(cacheDir);
    }

    /**
     * Refreshes every configured cache entry, sequentially. No-op when the cache is disabled or no
     * repositories are configured. Uses non-blocking locking so that an overlap between the startup
     * refresh and the scheduled cron tick is harmless - the later caller just returns.
     */
    public void refreshAll() {
        if (!isEnabled()) {
            log.debug("Reference repository cache disabled; skipping refresh");
            return;
        }
        List<RepositoryProperties> repositories = repositoryProperties.getRepositories();
        if (repositories == null || repositories.isEmpty()) {
            log.debug("No repositories configured; skipping cache refresh");
            return;
        }
        if (!refreshLock.tryLock()) {
            log.info("refreshAll: refresh already in progress; skipping");
            return;
        }
        long startNanos = System.nanoTime();
        try {
            File cacheRoot = new File(cacheProperties.getDirectory());
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
                log.error("Failed to create reference repository cache root directory {}", cacheRoot);
                return;
            }
            log.info("refreshAll: starting refresh of {} repositor(y/ies)", repositories.size());
            for (RepositoryProperties repo : repositories) {
                refresh(repo);
            }
            log.info("refreshAll: completed in {} ms", elapsedMs(startNanos));
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Refreshes a single configured cache entry, unconditionally. Used by
     * {@link MessageTypeRepository#checkoutAt}'s stale-cache retry when a requested commit/branch isn't in
     * the cache - the caller already paid the cost of a failed lookup, so we must hit upstream regardless
     * of the debounce window. Blocks on {@link #refreshLock} so only one upstream fetch runs at a time.
     * No-op when the cache is disabled or {@code gitUri} is not a configured repository.
     */
    public void refreshOne(String gitUri) {
        refreshOne(gitUri, false);
    }

    /**
     * Refreshes a single configured cache entry only if the previous successful refresh ended longer ago
     * than {@link MessageTypeRepositoryCacheProperties#getRefreshDebounceMillis()}. Used as the eager
     * pre-checkout refresh for branch-only contract uploads, so a moved branch tip is picked up while a
     * batch of concurrent uploads still pays at most one upstream round-trip. Implemented as a debounced
     * variant of {@link #refreshOne(String)}.
     */
    public void refreshIfStale(String gitUri) {
        refreshOne(gitUri, true);
    }

    private void refreshOne(String gitUri, boolean respectDebounce) {
        if (!isEnabled()) {
            return;
        }
        List<RepositoryProperties> repositories = repositoryProperties.getRepositories();
        if (repositories == null) {
            return;
        }
        Optional<RepositoryProperties> match = repositories.stream()
                .filter(r -> r.getUri().equals(gitUri))
                .findFirst();
        if (match.isEmpty()) {
            log.debug("refreshOne: gitUri={} is not a configured repository; skipping", gitUri);
            return;
        }
        if (respectDebounce && withinDebounceWindow(gitUri)) {
            log.debug("refreshOne: gitUri={} within debounce window; skipping (debounceMs={})", gitUri, cacheProperties.getRefreshDebounceMillis());
            return;
        }
        long lockWaitStart = System.nanoTime();
        refreshLock.lock();
        long lockWaitMs = elapsedMs(lockWaitStart);
        try {
            if (respectDebounce && withinDebounceWindow(gitUri)) {
                log.debug("refreshOne: gitUri={} debounced after acquiring lock (lockWaitMs={})", gitUri, lockWaitMs);
                return;
            }
            File cacheRoot = new File(cacheProperties.getDirectory());
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
                log.error("Failed to create reference repository cache root directory {}", cacheRoot);
                return;
            }
            long refreshStart = System.nanoTime();
            refresh(match.get());
            log.info("refreshOne: gitUri={} refresh done in {} ms (lockWaitMs={})", gitUri, elapsedMs(refreshStart), lockWaitMs);
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean withinDebounceWindow(String gitUri) {
        Long last = lastRefreshNanos.get(gitUri);
        if (last == null) {
            return false;
        }
        long debounceNanos = cacheProperties.getRefreshDebounceMillis() * 1_000_000L;
        return System.nanoTime() - last < debounceNanos;
    }

    private void refresh(RepositoryProperties repo) {
        File cacheDir = cacheDirFor(repo.getUri());
        CredentialsProvider credentials = createCredentialsProvider(repo);
        long startNanos = System.nanoTime();
        try {
            String operation = runRefresh(repo, cacheDir, credentials);
            lastRefreshNanos.put(repo.getUri(), System.nanoTime());
            log.info("refresh: {} for {} done in {} ms", operation, repo.getUri(), elapsedMs(startNanos));
        } catch (Exception ex) {
            log.error("Failed to refresh reference repository cache for {} after {} ms", repo.getUri(), elapsedMs(startNanos), ex);
        }
    }

    private String runRefresh(RepositoryProperties repo, File cacheDir, CredentialsProvider credentials) throws GitAPIException, IOException {
        if (isValidBareRepo(cacheDir)) {
            fetchExisting(cacheDir, credentials);
            return "incremental fetch";
        }
        if (cacheDir.exists()) {
            log.info("Removing invalid reference repository cache at {}", cacheDir);
            FileUtils.deleteDirectory(cacheDir);
        }
        cloneBare(repo.getUri(), cacheDir, credentials);
        return "full mirror clone";
    }

    @SuppressWarnings("EmptyTryBlock")
    private void cloneBare(String uri, File cacheDir, CredentialsProvider credentials) throws GitAPIException {
        try (Git _ = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(cacheDir)
                .setBare(true)
                .setMirror(true)
                .setCredentialsProvider(credentials)
                .call()) {
            // resources released; cache directory is now populated
        }
    }

    private void fetchExisting(File cacheDir, CredentialsProvider credentials) throws IOException, GitAPIException {
        try (Git git = Git.open(cacheDir)) {
            git.fetch()
                    .setRemote("origin")
                    .setCredentialsProvider(credentials)
                    .setRemoveDeletedRefs(true)
                    .setTagOpt(TagOpt.FETCH_TAGS)
                    .call();
        }
    }

    private boolean isValidBareRepo(File cacheDir) {
        return cacheDir.isDirectory()
                && new File(cacheDir, "HEAD").exists()
                && new File(cacheDir, "objects").isDirectory()
                && new File(cacheDir, "config").exists();
    }

    private File cacheDirFor(String gitUri) {
        return new File(cacheProperties.getDirectory(), hashUri(gitUri));
    }

    private CredentialsProvider createCredentialsProvider(RepositoryProperties repo) {
        if (RepositoryProperties.RepositoryType.GITHUB.equals(repo.getType())) {
            return GitHubAppCredentialsProvider.fromParameters(repo.getParameters(), meterRegistry);
        }
        return null;
    }

    private static String hashUri(String uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
