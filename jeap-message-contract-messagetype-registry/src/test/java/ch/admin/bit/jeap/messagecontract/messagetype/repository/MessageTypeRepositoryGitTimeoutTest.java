package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Verifies that the configured git timeout reaches the JGit clone and fetch commands, using a registry that accepts
 * connections but never answers. Without the timeout, these operations would wait forever.
 */
class MessageTypeRepositoryGitTimeoutTest {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path cacheRoot;

    private ServerSocket unresponsiveRegistry;
    private String unresponsiveRegistryUrl;
    private TestRegistryRepo repo;

    @BeforeEach
    void startUnresponsiveRegistry() throws Exception {
        // Connections are completed by the operating system's backlog, but nothing is ever read or answered
        unresponsiveRegistry = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        unresponsiveRegistryUrl = "http://127.0.0.1:%d/registry.git".formatted(unresponsiveRegistry.getLocalPort());
        repo = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterEach
    void stopUnresponsiveRegistry() throws Exception {
        unresponsiveRegistry.close();
        repo.delete();
    }

    @Test
    void defaultGitTimeoutIsThreeMinutes() {
        MessageTypeRepositoryProperties properties = new MessageTypeRepositoryProperties();

        assertThat(properties.getGitTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(properties.gitTimeoutSeconds()).isEqualTo(180);
    }

    @Test
    void gitTimeoutIsRoundedUpToWholeSeconds() {
        assertThat(MessageTypeRepositoryProperties.toGitTimeoutSeconds(Duration.ofMillis(1500))).isEqualTo(2);
        assertThat(MessageTypeRepositoryProperties.toGitTimeoutSeconds(Duration.ofSeconds(30))).isEqualTo(30);
        assertThat(MessageTypeRepositoryProperties.toGitTimeoutSeconds(Duration.ZERO)).isZero();
    }

    @Test
    void cloneTimesOutAsInfrastructureFailure() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(properties(), new SimpleMeterRegistry());

        MessageTypeRepoException exception = assertTimeoutPreemptively(TEST_TIMEOUT, () -> catchRepoException(() ->
                factory.cloneRepository(unresponsiveRegistryUrl).close()));

        assertThat(exception.isInfrastructureFailure()).isTrue();
        assertTimedOut(exception);
    }

    @Test
    void fetchTimesOutAsInfrastructureFailure() throws Exception {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(properties(), new SimpleMeterRegistry());
        try (MessageTypeRepository repository = factory.cloneRepository(repo.url())) {
            pointOriginAt(repository.gitRepoPath, unresponsiveRegistryUrl);

            MessageTypeRepoException exception = assertTimeoutPreemptively(TEST_TIMEOUT, () -> catchRepoException(() ->
                    repository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ")));

            assertThat(exception.isInfrastructureFailure()).isTrue();
            assertTimedOut(exception);
        }
    }

    @Test
    void commitFetchTimesOutAsInfrastructureFailure() throws Exception {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(properties(), new SimpleMeterRegistry());
        try (MessageTypeRepository repository = factory.cloneRepository(repo.url())) {
            pointOriginAt(repository.gitRepoPath, unresponsiveRegistryUrl);

            MessageTypeRepoException exception = assertTimeoutPreemptively(TEST_TIMEOUT, () -> catchRepoException(() ->
                    repository.getSchemaAsAvroProtocolJson("master", repo.revision(), "ActivZoneEnteredEvent", "1.0.0")));

            assertThat(exception.isInfrastructureFailure()).isTrue();
            assertTimedOut(exception);
        }
    }

    @Test
    void referenceCacheCloneAndFetchTimeOut() throws Exception {
        MessageTypeRepositoryCacheProperties cacheProperties = new MessageTypeRepositoryCacheProperties();
        cacheProperties.setDirectory(cacheRoot.toString());
        cacheProperties.setRefreshDebounceMillis(0);

        // full mirror clone from an unresponsive registry
        MessageTypeRepositoryReferenceCache unresponsiveCache = new MessageTypeRepositoryReferenceCache(
                cacheProperties, properties(unresponsiveRegistryUrl), new SimpleMeterRegistry());
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> unresponsiveCache.refreshOne(unresponsiveRegistryUrl));

        // incremental fetch of an existing cache whose registry stopped responding
        MessageTypeRepositoryReferenceCache cache = new MessageTypeRepositoryReferenceCache(
                cacheProperties, properties(repo.url()), new SimpleMeterRegistry());
        cache.refreshOne(repo.url());
        File cacheDir = cache.getCacheRepoDir(repo.url()).orElseThrow();
        pointOriginAt(cacheDir, unresponsiveRegistryUrl);
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> cache.refreshOne(repo.url()));
    }

    private static MessageTypeRepoException catchRepoException(Runnable action) {
        try {
            action.run();
        } catch (MessageTypeRepoException ex) {
            return ex;
        }
        throw new AssertionError("Expected a MessageTypeRepoException");
    }

    private static void assertTimedOut(Throwable exception) {
        assertThat(causes(exception)).anyMatch(cause -> cause instanceof SocketTimeoutException
                || String.valueOf(cause.getMessage()).toLowerCase().contains("timed out"));
    }

    private static List<Throwable> causes(Throwable throwable) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = throwable; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    private static void pointOriginAt(File repositoryDir, String url) throws Exception {
        try (Git git = Git.open(repositoryDir)) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", url);
            config.save();
        }
    }

    private MessageTypeRepositoryProperties properties() {
        MessageTypeRepositoryProperties properties = new MessageTypeRepositoryProperties();
        properties.setGitTimeout(GIT_TIMEOUT);
        return properties;
    }

    private MessageTypeRepositoryProperties properties(String uri) {
        RepositoryProperties repository = new RepositoryProperties();
        repository.setUri(uri);
        repository.setType(RepositoryProperties.RepositoryType.NONE);
        MessageTypeRepositoryProperties properties = properties();
        properties.setRepositories(List.of(repository));
        return properties;
    }
}
