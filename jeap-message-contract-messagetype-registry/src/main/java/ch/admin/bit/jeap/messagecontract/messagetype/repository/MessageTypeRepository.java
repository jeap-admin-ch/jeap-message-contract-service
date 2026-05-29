package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messaging.avro.plugin.validator.MessageTypeRegistryConstants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static ch.admin.bit.jeap.messagecontract.messagetype.repository.Elapsed.elapsedMs;
import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * Short-lived per-request worktree for a message type registry git repository.
 * <p>
 * Each instance is bound to a single git URI and is meant to be used inside a try-with-resources block:
 * {@link #cloneGitRepo()} provisions a fresh temporary directory and {@link #close()} deletes it.
 * <p>
 * The worktree is provisioned in one of two ways:
 * <ul>
 *   <li><b>Direct shallow clone</b> (no cache): a {@code git clone --depth 1 --no-tags} of {@code gitUri}.
 *   Subsequent ref lookups inside {@link #getSchemaAsAvroProtocolJson} run a per-checkout shallow fetch
 *   against the real upstream for the specific branch or commit reference.</li>
 *   <li><b>Cache-backed</b>: when {@link #setReferenceCacheDir(File)} has been wired by
 *   {@link MessageTypeRepositoryFactory} after consulting {@link MessageTypeRepositoryReferenceCache},
 *   the temporary worktree is created via {@code git init} and its {@code .git/objects/info/alternates}
 *   file is written to point at the cached bare repository's {@code objects} directory. Refs are then
 *   copied directly from the cache repository via {@link FileRepositoryBuilder} + {@link RefUpdate} - no
 *   JGit transport is opened. Every object is reachable through alternates, so checkout works against
 *   the local worktree without ever touching the real upstream. Per-request worktrees against an
 *   up-to-date cache pay zero network round-trips.</li>
 * </ul>
 * <p>
 * <b>Why no transport.</b> An earlier iteration used {@code remoteAdd origin=file://cache} +
 * {@code git fetch +HEAD --depth=1}. That path goes through JGit's {@code TransportLocal} + shallow
 * protocol and was observed to hang indefinitely on JGit 7.4-7.6 in containerised environments. Since the
 * cache shares all objects with the temp worktree through alternates, the fetch was buying us only refs
 * - and refs can be read out of the cache's on-disk ref database directly.
 * <p>
 * <b>Stale-cache retry.</b> When the cache is wired and a ref isn't present in the cache (typically
 * because the caller is referencing a commit pushed after the last cache refresh),
 * {@link #checkoutAt} runs the {@link #setCacheMissRefresh(Runnable) cacheMissRefresh} callback - which
 * the factory binds to {@link MessageTypeRepositoryReferenceCache#refreshOne(String)} - and retries once.
 * One real upstream round-trip is paid to update the cache (over JGit's HTTP transport, which works),
 * then all subsequent requests in the same batch hit the now-fresh cache locally.
 */
@SuppressWarnings("findbugs:NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
@Slf4j
public class MessageTypeRepository implements Closeable {
    private static final String COMMON = "_common";

    private final JsonFactory jsonFactory = new JsonFactory();
    private final JsonMapper jsonMapper;
    private final String gitUri;
    @Setter
    protected CredentialsProvider credentialsProvider;
    /**
     * Optional bare cache repo directory of a locally cached mirror of {@code gitUri}. When set, the temp
     * worktree shares this repository's {@code objects} via {@code .git/objects/info/alternates} and reads
     * refs (HEAD, branches, requested commit SHAs) directly from its on-disk ref database via
     * {@link FileRepositoryBuilder}. The temp worktree is never given an {@code origin} remote and never
     * opens a JGit transport against the cache. See the class Javadoc.
     */
    @Setter
    protected File referenceCacheDir;
    /**
     * Optional callback invoked by {@link #checkoutAt} when a per-checkout fetch+checkout against the
     * cache fails. Bound by {@link MessageTypeRepositoryFactory} to
     * {@link MessageTypeRepositoryReferenceCache#refreshOne(String)} so that a commit pushed after the
     * last refresh can be pulled into the cache before retrying. See the class Javadoc.
     */
    @Setter
    protected Runnable cacheMissRefresh;
    /**
     * Optional callback invoked before resolving a branch-only checkout (no commit SHA supplied) so that
     * an upstream branch tip that moved since the last cache refresh is picked up. Bound by
     * {@link MessageTypeRepositoryFactory} to
     * {@link MessageTypeRepositoryReferenceCache#refreshIfStale(String)} - debounced so a batch of
     * uploads pays at most one upstream round-trip.
     */
    @Setter
    protected Runnable eagerCacheRefresh;
    File gitRepoPath;
    private Git git;

    protected MessageTypeRepository(String gitUri) {
        this.jsonMapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        this.gitUri = gitUri;
    }

    @Override
    public void close() {
        forceDeleteDirectory(gitRepoPath);
    }

    public String getSchemaAsAvroProtocolJson(String branch, String commitReference, String messageTypeName, String messageTypeVersion) {
        log.info("Loading schema for {}:{}", messageTypeName, messageTypeVersion);
        try {
            checkoutAt(branch, commitReference);
        } catch (Exception ex) {
            throw MessageTypeRepoException.checkoutFailed(branch, commitReference, ex);
        }

        List<CommandDescriptor> commandDescriptors = getAllCommandDescriptors();
        List<EventDescriptor> eventDescriptors = getAllEventDescriptors();

        MessageTypeDescriptor descriptor = Stream.concat(eventDescriptors.stream(), commandDescriptors.stream())
                .filter(d -> messageTypeName.equals(d.getMessageTypeName()))
                .findFirst()
                .orElseThrow(MessageTypeRepoException.messageTypeNotFound(messageTypeName));

        MessageTypeVersion version = descriptor.findVersion(messageTypeVersion)
                .orElseThrow(MessageTypeRepoException.messageTypeVersionNotFound(messageTypeName, messageTypeVersion));
        String schemaFilename = version.getValueSchema();
        return AvroSchemaLoader.loadSchemaAsJsonProtocol(schemaFilename, descriptor.getSchemaLocations());
    }

    private void checkoutAt(String branch, String commitReference) throws GitAPIException {
        long startNanos = System.nanoTime();
        log.info("checkoutAt: enter branch={} commit={}", branch, commitReference);
        try {
            fetchAndCheckout(branch, commitReference);
        } catch (GitAPIException ex) {
            long firstAttemptElapsed = elapsedMs(startNanos);
            if (cacheMissRefresh == null) {
                log.warn("checkoutAt: fetch/checkout failed branch={} commit={} after {} ms; no cache to refresh, propagating", branch, commitReference, firstAttemptElapsed);
                throw ex;
            }
            log.info("checkoutAt: fetch/checkout failed branch={} commit={} after {} ms; refreshing reference cache and retrying ({}: {})", branch, commitReference, firstAttemptElapsed, ex.getClass().getSimpleName(), ex.getMessage());
            long refreshStart = System.nanoTime();
            try {
                cacheMissRefresh.run();
            } catch (RuntimeException refreshEx) {
                log.warn("checkoutAt: cache refresh-on-miss failed after {} ms", elapsedMs(refreshStart), refreshEx);
                throw ex;
            }
            log.debug("checkoutAt: cache refresh-on-miss completed in {} ms; retrying fetch/checkout", elapsedMs(refreshStart));
            fetchAndCheckout(branch, commitReference);
        }
        log.info("checkoutAt: completed branch={} commit={} totalElapsedMs={}", branch, commitReference, elapsedMs(startNanos));
    }

    private void fetchAndCheckout(String branch, String commitReference) throws GitAPIException {
        if (referenceCacheDir != null) {
            cacheRefCheckout(branch, commitReference);
        } else {
            directFetchAndCheckout(branch, commitReference);
        }
    }

    /**
     * Cache-mode checkout: read the requested ref from the cache repository directly, install it as a
     * local ref in the temp worktree (or check out by SHA for commit references), then checkout. No JGit
     * transport is opened. On a cache miss, throws {@link RefNotFoundException} so the
     * {@link #cacheMissRefresh} retry path in {@link #checkoutAt} fires.
     */
    private void cacheRefCheckout(String branch, String commitReference) throws GitAPIException {
        if (commitReference != null && !HEAD.equalsIgnoreCase(commitReference)) {
            cacheCheckoutCommit(commitReference);
        } else if (branch != null) {
            cacheCheckoutBranch(branch);
        }
    }

    private void cacheCheckoutCommit(String commitReference) throws GitAPIException {
        long resolveStart = System.nanoTime();
        ObjectId commitSha = resolveCommitInCache(commitReference);
        log.debug("cacheRefCheckout: commit {} resolved in {} ms", commitReference, elapsedMs(resolveStart));

        long checkoutStart = System.nanoTime();
        git.checkout()
                .setForced(true)
                .setName(commitSha.name())
                .call();
        log.debug("cacheRefCheckout: checkout commit={} done in {} ms", commitReference, elapsedMs(checkoutStart));
    }

    private void cacheCheckoutBranch(String branch) throws GitAPIException {
        // Branch-only checkout has no caller-supplied SHA to anchor on, so refresh the cache first to pick
        // up an upstream branch tip that may have moved since the last refresh. The eager-refresh callback
        // is debounced inside the cache so a batch of uploads pays at most one upstream round-trip.
        if (eagerCacheRefresh != null) {
            eagerCacheRefresh.run();
        }
        String localRef = Constants.R_HEADS + branch;
        long resolveStart = System.nanoTime();
        ObjectId branchSha = resolveBranchInCache(branch, localRef);
        log.debug("cacheRefCheckout: branch {} resolved to {} in {} ms", branch, branchSha.name(), elapsedMs(resolveStart));

        long installStart = System.nanoTime();
        installLocalRef(localRef, branchSha);
        log.debug("cacheRefCheckout: installed local ref {} in {} ms", localRef, elapsedMs(installStart));

        long checkoutStart = System.nanoTime();
        git.checkout()
                .setForced(true)
                .setName(branch)
                .call();
        log.debug("cacheRefCheckout: checkout branch={} done in {} ms", branch, elapsedMs(checkoutStart));
    }

    private ObjectId resolveCommitInCache(String commitReference) throws RefNotFoundException {
        if (!ObjectId.isId(commitReference)) {
            throw new RefNotFoundException("Invalid commit id: " + commitReference);
        }
        ObjectId commitSha = ObjectId.fromString(commitReference);
        try (Repository cacheRepo = openCacheRepository()) {
            cacheRepo.parseCommit(commitSha); // throws MissingObjectException if not present
        } catch (MissingObjectException ex) {
            throw new RefNotFoundException("Commit " + commitReference + " not present in reference cache", ex);
        } catch (IOException ex) {
            throw new JGitInternalException("Failed to read reference cache " + referenceCacheDir, ex);
        }
        return commitSha;
    }

    private ObjectId resolveBranchInCache(String branch, String localRef) throws RefNotFoundException {
        try (Repository cacheRepo = openCacheRepository()) {
            Ref cacheBranchRef = cacheRepo.exactRef(localRef);
            if (cacheBranchRef == null || cacheBranchRef.getObjectId() == null) {
                throw new RefNotFoundException("Branch " + branch + " not present in reference cache");
            }
            return cacheBranchRef.getObjectId();
        } catch (IOException ex) {
            throw new JGitInternalException("Failed to read reference cache " + referenceCacheDir, ex);
        }
    }

    private void installLocalRef(String localRef, ObjectId sha) {
        try {
            RefUpdate refUpdate = git.getRepository().updateRef(localRef);
            refUpdate.setNewObjectId(sha);
            refUpdate.setForceUpdate(true);
            refUpdate.update();
        } catch (IOException ex) {
            throw new JGitInternalException("Failed to install local ref " + localRef, ex);
        }
    }

    /**
     * Direct-clone (no-cache) fallback: shallow fetch the requested ref from the real upstream and check
     * it out. This is the original JEAP-7002 behaviour, kept for environments without the cache.
     */
    private void directFetchAndCheckout(String branch, String commitReference) throws GitAPIException {
        if (commitReference != null && !HEAD.equalsIgnoreCase(commitReference)) {
            long fetchStart = System.nanoTime();
            git.fetch()
                    .setCredentialsProvider(this.credentialsProvider)
                    .setDepth(1)
                    .setRefSpecs(new RefSpec(commitReference))
                    .call();
            log.debug("fetchAndCheckout: fetch commit={} done in {} ms", commitReference, elapsedMs(fetchStart));
            long checkoutStart = System.nanoTime();
            git.checkout()
                    .setForced(true)
                    .setName(commitReference)
                    .call();
            log.debug("fetchAndCheckout: checkout commit={} done in {} ms", commitReference, elapsedMs(checkoutStart));
        } else if (branch != null) {
            long fetchStart = System.nanoTime();
            git.fetch()
                    .setCredentialsProvider(this.credentialsProvider)
                    .setDepth(1)
                    .setRefSpecs(new RefSpec("+refs/heads/" + branch + ":refs/remotes/origin/" + branch))
                    .call();
            log.debug("fetchAndCheckout: fetch branch={} done in {} ms", branch, elapsedMs(fetchStart));
            long checkoutStart = System.nanoTime();
            git.checkout()
                    .setForced(true)
                    .setName("origin/" + branch)
                    .call();
            log.debug("fetchAndCheckout: checkout branch={} done in {} ms", branch, elapsedMs(checkoutStart));
        }
    }

    private List<EventDescriptor> getAllEventDescriptors() {
        return getSystemDirs()
                .flatMap(systemDir -> getMessageTypeDirs(systemDir, EventDescriptor.SUBDIR))
                .flatMap(messageTypeDir -> tryReadDescriptor(messageTypeDir, EventDescriptor.class).stream())
                .toList();
    }

    private List<CommandDescriptor> getAllCommandDescriptors() {
        return getSystemDirs()
                .flatMap(systemDir -> getMessageTypeDirs(systemDir, CommandDescriptor.SUBDIR))
                .flatMap(messageTypeDir -> tryReadDescriptor(messageTypeDir, CommandDescriptor.class).stream())
                .toList();
    }

    /**
     * Provisions the temporary worktree backing this repository.
     * <p>
     * If {@link #referenceCacheDir} is set, delegates to
     * {@link #cloneWithReference(File)} which sets up a {@code git init} + alternates layout and copies
     * the cache's HEAD ref directly - no JGit transport is opened against the cache.
     * Otherwise performs a {@code clone --depth 1 --no-tags} from {@code gitUri}.
     */
    public void cloneGitRepo() {
        long startNanos = System.nanoTime();
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("messageTypeRepo").toFile(); // NOSONAR
            FileUtils.forceDeleteOnExit(tempDir);
            if (referenceCacheDir != null) {
                log.info("cloneGitRepo: cache-backed clone of {} into {} using reference cache {}", gitUri, tempDir, referenceCacheDir);
                this.git = cloneWithReference(tempDir);
                log.info("cloneGitRepo: cache-backed clone of {} done in {} ms", gitUri, elapsedMs(startNanos));
            } else {
                log.info("cloneGitRepo: direct shallow clone of {} into {}", gitUri, tempDir);
                this.git = Git.cloneRepository()
                        .setURI(gitUri)
                        .setDirectory(tempDir)
                        .setCredentialsProvider(this.credentialsProvider)
                        .setDepth(1)
                        .setNoTags()
                        .call();
                log.info("cloneGitRepo: direct shallow clone of {} done in {} ms", gitUri, elapsedMs(startNanos));
            }
            this.gitRepoPath = tempDir;
        } catch (IOException | GitAPIException e) {
            forceDeleteDirectory(tempDir);
            throw MessageTypeRepoException.cloneFailed(gitUri, e);
        }
    }

    /**
     * Builds a cache-backed worktree at {@code tempDir} without opening any JGit transport.
     * <ol>
     *   <li>{@code git init} the directory.</li>
     *   <li>Write {@code .git/objects/info/alternates} pointing at the cache's {@code objects} dir so that
     *       every object already present in the cached bare mirror is reachable without re-downloading.</li>
     *   <li>Open the cache repository read-only via {@link FileRepositoryBuilder}, read its symbolic
     *       {@code HEAD}, and resolve the target branch name + commit SHA.</li>
     *   <li>Install that branch ref locally in the temp repo via {@link RefUpdate}, then point HEAD at it
     *       symbolically.</li>
     *   <li>{@code git.checkout()} the freshly installed ref - objects are reachable through alternates.</li>
     * </ol>
     * Cache staleness is handled in {@link #checkoutAt} via {@link #cacheMissRefresh}.
     */
    private Git cloneWithReference(File tempDir) throws IOException, GitAPIException {
        long stepStart = System.nanoTime();
        Git initializedGit = Git.init().setDirectory(tempDir).call();
        log.debug("cloneWithReference: git init done in {} ms", elapsedMs(stepStart));
        try {
            File cacheObjectsDir = new File(referenceCacheDir, "objects");
            stepStart = System.nanoTime();
            Path alternates = tempDir.toPath().resolve(".git/objects/info/alternates");
            Files.createDirectories(alternates.getParent());
            Files.writeString(alternates, cacheObjectsDir.getAbsolutePath() + System.lineSeparator());
            log.debug("cloneWithReference: alternates written in {} ms", elapsedMs(stepStart));

            stepStart = System.nanoTime();
            String targetRefName;
            ObjectId targetSha;
            try (Repository cacheRepo = openCacheRepository()) {
                Ref cacheHead = cacheRepo.exactRef(Constants.HEAD);
                if (cacheHead == null) {
                    throw new IOException("Reference cache " + referenceCacheDir + " has no HEAD");
                }
                Ref leaf = cacheHead.getLeaf();
                targetRefName = leaf.getName();
                targetSha = leaf.getObjectId();
                if (targetSha == null) {
                    throw new IOException("Reference cache HEAD does not resolve to a commit: " + cacheHead);
                }
            }
            log.debug("cloneWithReference: cache HEAD resolved to {} @ {} in {} ms", targetRefName, targetSha.name(), elapsedMs(stepStart));

            stepStart = System.nanoTime();
            Repository tempRepo = initializedGit.getRepository();
            RefUpdate refUpdate = tempRepo.updateRef(targetRefName);
            refUpdate.setNewObjectId(targetSha);
            refUpdate.setForceUpdate(true);
            refUpdate.update();
            RefUpdate headUpdate = tempRepo.updateRef(Constants.HEAD);
            headUpdate.link(targetRefName);
            log.debug("cloneWithReference: local ref {} installed in {} ms", targetRefName, elapsedMs(stepStart));

            stepStart = System.nanoTime();
            initializedGit.checkout()
                    .setForced(true)
                    .setName(targetRefName)
                    .call();
            log.debug("cloneWithReference: checkout {} done in {} ms", targetRefName, elapsedMs(stepStart));
            return initializedGit;
        } catch (Exception ex) {
            initializedGit.close();
            throw ex;
        }
    }

    /**
     * Opens the cache repository read-only. Caller must close.
     */
    private Repository openCacheRepository() throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(referenceCacheDir)
                .setMustExist(true)
                .build();
    }

    private Stream<File> getSystemDirs() {
        File descriptorDir = new File(gitRepoPath, "descriptor");
        return Arrays.stream(Objects.requireNonNull(descriptorDir.list()))
                .filter(name -> !name.equals(COMMON))
                .map(name -> new File(descriptorDir, name));
    }

    private static Stream<File> getMessageTypeDirs(File systemDir, String typeSubdir) {
        File typeDir = new File(systemDir, typeSubdir);
        if (!typeDir.isDirectory()) {
            return Stream.empty();
        }
        return Arrays.stream(Objects.requireNonNull(typeDir.list()))
                .map(eventDirName -> new File(typeDir, eventDirName));
    }

    private File getDescriptorFile(File messageTypeDir) {
        String[] jsonFileNames = messageTypeDir.list((_, n) -> FilenameUtils.getExtension(n).equals("json"));
        if (jsonFileNames == null || jsonFileNames.length == 0) {
            throw MessageTypeRepoException.missingDescriptor(messageTypeDir.getAbsolutePath());
        }
        return new File(messageTypeDir, jsonFileNames[0]);
    }

    private <T extends MessageTypeDescriptor> Optional<T> tryReadDescriptor(File messageTypeDir, Class<T> descriptorType) {
        try {
            File descriptorFile = getDescriptorFile(messageTypeDir);
            return Optional.of(readDescriptor(descriptorFile, descriptorType));
        } catch (MessageTypeRepoException ex) {
            log.warn("Failed to load message type descriptor {}", messageTypeDir, ex);
            return Optional.empty();
        }
    }

    private <T extends MessageTypeDescriptor> T readDescriptor(File descriptorFile, Class<T> descriptorType) {
        log.trace("Loading message type descriptor {}", descriptorFile);
        try {
            JsonParser jsonParser = jsonFactory.createParser(descriptorFile);
            T descriptor = jsonMapper.readValue(jsonParser, descriptorType);
            File messageTypeDir = descriptorFile.getParentFile();
            File systemCommonDir = new File(messageTypeDir.getParentFile().getParentFile(), MessageTypeRegistryConstants.COMMON_DIR_NAME);
            File commonDir = new File(systemCommonDir.getParentFile().getParentFile(), MessageTypeRegistryConstants.COMMON_DIR_NAME);
            SchemaLocations schemaLocations = new SchemaLocations(messageTypeDir, systemCommonDir, commonDir);
            descriptor.setSchemaLocations(schemaLocations);
            return descriptor;
        } catch (IOException e) {
            throw MessageTypeRepoException.descriptorParsingFailed(descriptorFile.getAbsolutePath(), e);
        }
    }

    private static void forceDeleteDirectory(File dir) {
        if (dir == null) {
            return;
        }
        try {
            log.info("Deleting {}", dir);
            FileUtils.forceDelete(dir);
        } catch (IOException e) {
            log.error("Failed to delete {}", dir, e);
        }
    }

}
