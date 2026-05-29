package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTypeRepositoryReferenceCacheTest {

    @TempDir
    Path cacheRoot;

    private TestRegistryRepo repo;
    private String repoUrl;

    @BeforeEach
    void prepareRepository() throws Exception {
        repo = TestRegistryRepo.createMessageTypeRegistryRepository();
        repoUrl = repo.url();
    }

    @AfterEach
    void deleteRepository() throws Exception {
        repo.delete();
    }

    @Test
    void refreshAll_whenCacheDisabled_doesNothing() {
        MessageTypeRepositoryCacheProperties cacheProps = new MessageTypeRepositoryCacheProperties();
        cacheProps.setEnabled(false);
        cacheProps.setDirectory(cacheRoot.toString());
        MessageTypeRepositoryReferenceCache cache = new MessageTypeRepositoryReferenceCache(
                cacheProps, propertiesFor(repoUrl), new SimpleMeterRegistry());

        cache.refreshAll();

        assertThat(cacheRoot).isEmptyDirectory();
        assertThat(cache.getCacheRepoDir(repoUrl)).isEmpty();
    }

    @Test
    void refreshAll_createsCacheEntryForConfiguredRepository() {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);

        cache.refreshAll();

        Optional<File> cacheDir = cache.getCacheRepoDir(repoUrl);
        assertThat(cacheDir).isPresent();
        assertThat(cacheDir.get())
                .isDirectory()
                .isDirectoryContaining(f -> f.getName().equals("HEAD"))
                .isDirectoryContaining(f -> f.getName().equals("config"))
                .isDirectoryContaining(f -> f.getName().equals("objects"));
    }

    @Test
    void refreshAll_secondCallUpdatesExistingCache() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);

        cache.refreshAll();
        File cacheDir = cache.getCacheRepoDir(repoUrl).orElseThrow();
        ObjectId masterBefore = readMasterSha(cacheDir);

        Path newFile = repo.repoDir().resolve("descriptor/activ/event/newevent/NewEvent.json");
        repo.addAndCommitFile(newFile, "{\"messageTypeName\":\"NewEvent\"}");
        ObjectId upstreamHead;
        try (Git upstream = Git.open(repo.repoDir().toFile())) {
            upstreamHead = upstream.getRepository().resolve("refs/heads/master");
        }

        cache.refreshAll();

        ObjectId masterAfter = readMasterSha(cacheDir);
        assertThat(masterAfter)
                .isNotNull()
                .isNotEqualTo(masterBefore);
        assertThat(masterAfter.name()).isEqualTo(upstreamHead.name());
    }

    @Test
    void refreshAll_invalidCacheEntryIsRecreated() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);

        cache.refreshAll();
        File cacheDir = cache.getCacheRepoDir(repoUrl).orElseThrow();
        FileUtils.delete(new File(cacheDir, "HEAD"));
        assertThat(cache.getCacheRepoDir(repoUrl)).isEmpty();

        cache.refreshAll();

        assertThat(cache.getCacheRepoDir(repoUrl)).isPresent();
    }

    @Test
    void refreshAll_failedRefreshDoesNotThrow() {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache("file:///this-does-not-exist");

        cache.refreshAll();

        assertThat(cache.getCacheRepoDir("file:///this-does-not-exist")).isEmpty();
    }

    @Test
    void factory_clonesKnownRepoUsingAlternates_andSchemaLoadingStillWorks() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);
        cache.refreshAll();

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor(repoUrl), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            Path alternatesFile = messageTypeRepository.gitRepoPath.toPath()
                    .resolve(".git/objects/info/alternates");
            assertThat(alternatesFile).exists();
            String referenceLine = Files.readString(alternatesFile).trim();
            File expectedObjectsDir = new File(cache.getCacheRepoDir(repoUrl).orElseThrow(), "objects");
            assertThat(referenceLine).isEqualTo(expectedObjectsDir.getAbsolutePath());

            String schemaFromBranch = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromBranch).contains("ZoneReference");

            String schemaFromCommit = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromCommit).contains("ZoneReference");
        }
    }

    @Test
    void factory_cacheHitDoesNotOpenAnyTransport_andServesFromAlternatesOnly() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);
        cache.refreshAll();

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor(repoUrl), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            try (Git tempGit = Git.open(messageTypeRepository.gitRepoPath)) {
                String originUrl = tempGit.getRepository().getConfig().getString("remote", "origin", "url");
                assertThat(originUrl)
                        .as("temp worktree must have no origin remote - we never open a JGit transport against the cache")
                        .isNull();
            }

            String schemaFromBranch = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromBranch).contains("ZoneReference");
        }
    }

    @Test
    void factory_clonesUnknownRepoDirectlyWithoutAlternates() {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache("https://example.invalid/other.git");
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor("https://example.invalid/other.git"), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            File alternatesFile = messageTypeRepository.gitRepoPath.toPath()
                    .resolve(".git/objects/info/alternates").toFile();
            assertThat(alternatesFile).doesNotExist();
        }
    }

    @Test
    void factory_cacheHitServesSchemaWithoutContactingUpstream() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);
        cache.refreshAll();

        repo.delete();

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor(repoUrl), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaFromBranch = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromBranch).contains("ZoneReference");

            String schemaFromCommit = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromCommit).contains("ZoneReference");
        } finally {
            repo = TestRegistryRepo.createMessageTypeRegistryRepository();
        }
    }

    @Test
    void factory_staleCacheTriggersRefreshAndServesNewCommit() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);
        cache.refreshAll();
        File cacheDir = cache.getCacheRepoDir(repoUrl).orElseThrow();
        ObjectId beforeNewCommit = readMasterSha(cacheDir);

        Path newDescriptor = repo.repoDir().resolve("descriptor/activ/event/freshevent/FreshEvent.json");
        repo.addAndCommitFile(newDescriptor, "{\"messageTypeName\":\"FreshEvent\"}");
        String newCommitSha;
        try (Git upstream = Git.open(repo.repoDir().toFile())) {
            newCommitSha = upstream.getRepository().resolve("refs/heads/master").name();
        }

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor(repoUrl), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaFromNewCommit = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, newCommitSha, "ActivZoneEnteredEvent", "1.0.0");
            assertThat(schemaFromNewCommit).contains("ZoneReference");
        }

        ObjectId afterFetch = readMasterSha(cacheDir);
        assertThat(afterFetch).isNotEqualTo(beforeNewCommit);
        assertThat(afterFetch.name()).isEqualTo(newCommitSha);
    }

    @Test
    void factory_branchCheckout_picksUpMovedUpstreamTip() throws Exception {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl);
        cache.refreshAll();
        File cacheDir = cache.getCacheRepoDir(repoUrl).orElseThrow();
        ObjectId cachedMasterBefore = readMasterSha(cacheDir);

        // Move the upstream master tip after the cache was last refreshed.
        Path newDescriptor = repo.repoDir().resolve("descriptor/activ/event/movedbranchevent/MovedBranchEvent.json");
        repo.addAndCommitFile(newDescriptor, "{\"messageTypeName\":\"MovedBranchEvent\"}");
        ObjectId upstreamMasterAfter;
        try (Git upstream = Git.open(repo.repoDir().toFile())) {
            upstreamMasterAfter = upstream.getRepository().resolve("refs/heads/master");
        }
        assertThat(upstreamMasterAfter).isNotEqualTo(cachedMasterBefore);

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(
                propertiesFor(repoUrl), new SimpleMeterRegistry(), cache);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            // Branch-only request must trigger the eager cache refresh; otherwise we'd resolve to the
            // stale cachedMasterBefore SHA.
            messageTypeRepository.getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
        }

        ObjectId cachedMasterAfter = readMasterSha(cacheDir);
        assertThat(cachedMasterAfter.name()).isEqualTo(upstreamMasterAfter.name());
    }

    @Test
    void refreshIfStale_debouncesConcurrentCallsWithinWindow() {
        MessageTypeRepositoryReferenceCache cache = newEnabledCache(repoUrl, 60_000L);
        cache.refreshAll();
        File cacheDir = cache.getCacheRepoDir(repoUrl).orElseThrow();
        long lastModifiedBefore = new File(cacheDir, "HEAD").lastModified();

        // Multiple eager refreshes inside the debounce window should be no-ops; cache stays untouched.
        cache.refreshIfStale(repoUrl);
        cache.refreshIfStale(repoUrl);
        cache.refreshIfStale(repoUrl);

        long lastModifiedAfter = new File(cacheDir, "HEAD").lastModified();
        assertThat(lastModifiedAfter).isEqualTo(lastModifiedBefore);
    }

    private MessageTypeRepositoryReferenceCache newEnabledCache(String knownUri) {
        return newEnabledCache(knownUri, 0L);
    }

    private MessageTypeRepositoryReferenceCache newEnabledCache(String knownUri, long debounceMillis) {
        MessageTypeRepositoryCacheProperties cacheProps = new MessageTypeRepositoryCacheProperties();
        cacheProps.setEnabled(true);
        cacheProps.setDirectory(cacheRoot.toString());
        cacheProps.setRefreshDebounceMillis(debounceMillis);
        return new MessageTypeRepositoryReferenceCache(
                cacheProps, propertiesFor(knownUri), new SimpleMeterRegistry());
    }

    private MessageTypeRepositoryProperties propertiesFor(String uri) {
        MessageTypeRepositoryProperties props = new MessageTypeRepositoryProperties();
        RepositoryProperties repoProps = new RepositoryProperties();
        repoProps.setUri(uri);
        repoProps.setType(RepositoryProperties.RepositoryType.NONE);
        props.setRepositories(List.of(repoProps));
        return props;
    }

    private ObjectId readMasterSha(File cacheDir) throws Exception {
        try (Git git = Git.open(cacheDir)) {
            return git.getRepository().resolve("refs/heads/master");
        }
    }
}
