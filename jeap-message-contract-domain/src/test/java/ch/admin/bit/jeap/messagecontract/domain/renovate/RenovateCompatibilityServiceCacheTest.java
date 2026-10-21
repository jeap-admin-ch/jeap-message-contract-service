package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.renovate.RenovateLookups.SnapshotKey;
import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.DeployedMessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractSchema;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the caching of the Renovate lookups with the real cache manager and the Spring caching proxy. The time to
 * live elapses through a fake {@link Ticker}.
 */
@SpringJUnitConfig(classes = {RenovateCompatibilityServiceCacheTest.TestConfig.class, RenovateCacheConfiguration.class,
        RenovateLookups.class, RenovateCompatibilityService.class})
class RenovateCompatibilityServiceCacheTest {

    static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.activ:activ-zone-entered-event";
    private static final String SNAPSHOT_COMMIT = "0123456789012345678901234567890123456789";
    private static final SnapshotKey SNAPSHOT_KEY =
            new SnapshotKey("registry-url", "master", "ActivZoneEnteredEvent", "activ");

    @Autowired
    private RenovateCompatibilityService service;
    @Autowired
    private RenovateLookups lookups;
    @Autowired
    @Qualifier(RenovateCacheConfiguration.CACHE_MANAGER)
    private CacheManager cacheManager;
    @Autowired
    private FakeTicker ticker;
    @MockitoBean
    private MessageContractRepository contractRepository;
    @MockitoBean
    private MessageTypeRepositoryFactory repositoryFactory;
    @MockitoBean
    private MessageSchemaService schemaService;
    @MockitoBean
    private SchemaCompatibilityService compatibilityService;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    void cacheManagerHasOnlyTheRenovateCaches() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(
                "renovate-deployed-contracts", "renovate-contract-schemas",
                "renovate-registry-snapshots", "renovate-candidate-schemas");
        assertThat(cacheManager.getCache("other")).isNull();
    }

    @Test
    void repeatedRequestsWithinTtlAreServedFromCache() {
        DeployedMessageContract requestingConsumer = contract("requesting", MessageContractRole.CONSUMER);
        DeployedMessageContract deployedProducer = contract("producer", MessageContractRole.PRODUCER);
        MessageTypeRepository typeRepository = givenDeployedContractsAndRegistry(requestingConsumer, deployedProducer);
        when(contractRepository.findSchemas(anyCollection())).thenReturn(List.of(
                new MessageContractSchema(deployedProducer.id(), "producer-protocol", null)));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        for (int i = 0; i < 3; i++) {
            assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "prod"))
                    .extracting(RenovateRelease::version).containsExactly("1.1.0");
            assertThat(service.findCompatibleReleasesForApp("requesting", PACKAGE_NAME, "1.0.0", "PROD"))
                    .extracting(RenovateRelease::version).containsExactly("1.1.0");
        }
        ticker.advance(Duration.ofMinutes(4));
        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "0.9.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.0.0", "1.1.0");

        verify(contractRepository, times(1)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
        verify(repositoryFactory, times(1)).cloneRepository("registry-url");
        verify(typeRepository, times(1)).getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ");
        // the candidate schema is loaded once per candidate version: 1.1.0, and 1.0.0 for the last request
        verify(schemaService, times(2)).loadSchemas(anyList());
        // the schemas are cached per set of compared contracts: all in global mode, the counterparts for the app
        verify(contractRepository, times(2)).findSchemas(anyCollection());
    }

    @Test
    void cachedCandidateSchemaIsSetOnEveryNewCandidate() {
        DeployedMessageContract deployedProducer = contract("producer", MessageContractRole.PRODUCER);
        givenDeployedContractsAndRegistry(deployedProducer);
        doAnswer(invocation -> {
            List<MessageContract> candidates = invocation.getArgument(0);
            candidates.forEach(candidate -> candidate.setAvroProtocolSchema("candidate-protocol"));
            return null;
        }).when(schemaService).loadSchemas(anyList());
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");

        verify(schemaService, times(1)).loadSchemas(anyList());
        var readers = org.mockito.ArgumentCaptor.forClass(MessageContract.class);
        verify(compatibilityService, times(2)).validateReaderWriterCompatibility(readers.capture(), any());
        assertThat(readers.getAllValues()).hasSize(2)
                .allMatch(reader -> "candidate-protocol".equals(reader.getAvroProtocolSchema()));
        assertThat(readers.getAllValues().get(0)).isNotSameAs(readers.getAllValues().get(1));
    }

    @Test
    void requestsAreAnsweredFromCurrentDataAfterTtlExpired() {
        givenDeployedContractsAndRegistry(contract("producer", MessageContractRole.PRODUCER));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        ticker.advance(Duration.ofMinutes(5));
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");

        verify(contractRepository, times(2)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
        verify(repositoryFactory, times(2)).cloneRepository("registry-url");
        verify(schemaService, times(2)).loadSchemas(anyList());
    }

    @Test
    void cacheIsPerEnvironment() {
        when(contractRepository.findCurrentlyDeployedContracts(anyString(), anyString())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "REF");
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");

        verify(contractRepository, times(1)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
        verify(contractRepository, times(1)).findCurrentlyDeployedContracts("REF", "activzoneenteredevent");
    }

    @Test
    void registryFailureIsNotCachedAndReportedAsUnavailable() {
        MessageTypeRepository typeRepository = givenDeployedContractsAndRegistry(
                contract("producer", MessageContractRole.PRODUCER));
        MessageTypeRepoException failure = MessageTypeRepoException.checkoutFailed(
                "master", null, new IllegalStateException("registry down"));
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenThrow(failure)
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .isInstanceOf(RenovateRegistryException.class)
                .hasCause(failure);
        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.1.0");
        verify(repositoryFactory, times(2)).cloneRepository("registry-url");
    }

    @Test
    void invalidRegistryContentIsNotCachedAndFailsClosed() {
        MessageTypeRepository typeRepository = givenDeployedContractsAndRegistry(
                contract("producer", MessageContractRole.PRODUCER));
        // Only an unwrapped exception lets the service tell invalid content from an unavailable registry
        MessageTypeRepoException invalidContent = mock(MessageTypeRepoException.class);
        when(invalidContent.isInfrastructureFailure()).thenReturn(false);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenThrow(invalidContent)
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD")).isEmpty();
        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.1.0");
    }

    @Test
    void loaderExceptionsReachTheCallerUnwrapped() {
        MessageTypeRepoException failure = MessageTypeRepoException.checkoutFailed(
                "master", null, new IllegalStateException("registry down"));
        when(repositoryFactory.cloneRepository("registry-url")).thenThrow(failure);
        IllegalStateException databaseFailure = new IllegalStateException("database down");
        when(contractRepository.findCurrentlyDeployedContracts(anyString(), anyString())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> lookups.registrySnapshot(SNAPSHOT_KEY)).isSameAs(failure);
        assertThatThrownBy(() -> lookups.deployedContracts(
                new RenovateLookups.DeployedContractsKey("PROD", "activzoneenteredevent"))).isSameAs(databaseFailure);
    }

    @Test
    void concurrentRequestWaitsForTheInFlightLoad() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(repositoryFactory.cloneRepository("registry-url")).thenAnswer(invocation -> {
            loading.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return typeRepository;
        });
        MessageTypeRepository.MessageTypeSnapshot snapshot =
                new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0"));
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ")).thenReturn(snapshot);

        CompletableFuture<MessageTypeRepository.MessageTypeSnapshot> first =
                CompletableFuture.supplyAsync(() -> lookups.registrySnapshot(SNAPSHOT_KEY));
        assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        CompletableFuture<MessageTypeRepository.MessageTypeSnapshot> second = CompletableFuture.supplyAsync(() -> {
            secondThread.set(Thread.currentThread());
            return lookups.registrySnapshot(SNAPSHOT_KEY);
        });
        awaitBlockedInCache(secondThread);
        release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(snapshot);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(snapshot);
        verify(repositoryFactory, times(1)).cloneRepository("registry-url");
    }

    @Test
    void failedInFlightLoadIsNotHandedToTheWaitingRequestWhichLoadsAgain() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MessageTypeRepoException failure = MessageTypeRepoException.checkoutFailed(
                "master", null, new IllegalStateException("registry down"));
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        MessageTypeRepository.MessageTypeSnapshot snapshot =
                new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0"));
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ")).thenReturn(snapshot);
        AtomicLong clones = new AtomicLong();
        when(repositoryFactory.cloneRepository("registry-url")).thenAnswer(invocation -> {
            if (clones.incrementAndGet() == 1) {
                loading.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                throw failure;
            }
            return typeRepository;
        });

        CompletableFuture<MessageTypeRepository.MessageTypeSnapshot> first =
                CompletableFuture.supplyAsync(() -> lookups.registrySnapshot(SNAPSHOT_KEY));
        assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        CompletableFuture<MessageTypeRepository.MessageTypeSnapshot> second = CompletableFuture.supplyAsync(() -> {
            secondThread.set(Thread.currentThread());
            return lookups.registrySnapshot(SNAPSHOT_KEY);
        });
        awaitBlockedInCache(secondThread);
        release.countDown();

        assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(failure);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(snapshot);
        verify(repositoryFactory, times(2)).cloneRepository("registry-url");
    }

    /**
     * Waits until the thread is parked in the cache waiting for the in-flight load of the same key, so that the test
     * proves the waiting instead of a lucky ordering of the threads.
     */
    private static void awaitBlockedInCache(AtomicReference<Thread> threadReference)
            throws InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        EnumSet<Thread.State> waitingStates =
                EnumSet.of(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null && waitingStates.contains(thread.getState()) && Arrays.stream(thread.getStackTrace())
                    .anyMatch(frame -> frame.getClassName().startsWith("com.github.benmanes.caffeine.cache."))) {
                return;
            }
            Thread.sleep(5);
        }
        throw new TimeoutException("The second request did not wait for the in-flight load");
    }

    MessageTypeRepository givenDeployedContractsAndRegistry(DeployedMessageContract... deployedContracts) {
        return givenDeployedContractsAndRegistry(contractRepository, repositoryFactory, deployedContracts);
    }

    static MessageTypeRepository givenDeployedContractsAndRegistry(MessageContractRepository contractRepository,
                                                                   MessageTypeRepositoryFactory repositoryFactory,
                                                                   DeployedMessageContract... deployedContracts) {
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(deployedContracts));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.0.0", "1.1.0")));
        return typeRepository;
    }

    static DeployedMessageContract contract(String appName, MessageContractRole role) {
        return new DeployedMessageContract(UUID.randomUUID(), appName, "1.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", role, "registry-url", "master", CompatibilityMode.NONE, null);
    }

    static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RenovateProperties.class)
    static class TestConfig {

        @Bean
        @Qualifier(RenovateCacheConfiguration.CACHE_MANAGER)
        FakeTicker renovateCacheTicker() {
            return new FakeTicker();
        }
    }
}
