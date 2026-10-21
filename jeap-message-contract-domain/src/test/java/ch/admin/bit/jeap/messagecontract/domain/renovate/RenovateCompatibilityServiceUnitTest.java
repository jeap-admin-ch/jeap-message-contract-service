package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaIncompatibility;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class RenovateCompatibilityServiceUnitTest {

    private static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.activ:activ-zone-entered-event";
    private static final String SNAPSHOT_COMMIT = "0123456789012345678901234567890123456789";

    private final MessageContractRepository contractRepository = mock(MessageContractRepository.class);
    private final MessageTypeRepositoryFactory repositoryFactory = mock(MessageTypeRepositoryFactory.class);
    private final MessageSchemaService schemaService = mock(MessageSchemaService.class);
    private final SchemaCompatibilityService compatibilityService = mock(SchemaCompatibilityService.class);
    private final MutableClock clock = new MutableClock();
    private RenovateCompatibilityService service;

    @BeforeEach
    void setUp() {
        service = new RenovateCompatibilityService(contractRepository, repositoryFactory, schemaService,
                compatibilityService, new RenovateProperties(), clock);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validatesRequestBeforePersistenceOrRegistryWork(String packageName, String currentValue,
                                                          String environment) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.findGloballyCompatibleReleases(packageName, currentValue, environment));

        verifyNoInteractions(contractRepository, repositoryFactory);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("not-a-coordinate", "1.0.0", "PROD"),
                Arguments.of("ch.admin.bit.jeap.messagetype.activ:bad artifact", "1.0.0", "PROD"),
                Arguments.of("ch..admin.bit.jeap.messagetype.activ:artifact", "1.0.0", "PROD"),
                Arguments.of("ch.admin.bit.jeap.messagetype.activ:bad..artifact", "1.0.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "1.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "-1.0.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "01.0.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "1.0.0", " "));
    }

    @Test
    void candidateSchemaIsLoadedFromEnumerationSnapshotCommit() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(deployedProducer));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.1.0");

        ArgumentCaptor<List<MessageContract>> contracts = ArgumentCaptor.forClass(List.class);
        verify(schemaService).loadSchemas(contracts.capture());
        assertThat(contracts.getValue().getFirst().getCommitHash()).isEqualTo(SNAPSHOT_COMMIT);
    }

    @Test
    void registryCheckoutFailureIsReportedAsUnavailable() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(deployedProducer));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenThrow(MessageTypeRepoException.checkoutFailed(
                        "master", null, new IllegalStateException("checkout failed")));

        assertThatThrownBy(() -> service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .isInstanceOf(RenovateRegistryException.class);
    }

    @Test
    void candidateSchemaInfrastructureFailureIsReportedAsUnavailable() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(deployedProducer));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        doThrow(MessageTypeRepoException.checkoutFailed(
                null, SNAPSHOT_COMMIT, new IllegalStateException("checkout failed")))
                .when(schemaService).loadSchemas(anyList());

        assertThatThrownBy(() -> service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .isInstanceOf(RenovateRegistryException.class);
    }

    @Test
    void appCandidateIsRejectedWhenOneOfMultipleSameTopicCounterpartsIsIncompatible() {
        DeployedMessageContract requestingConsumer = contract("requesting", MessageContractRole.CONSUMER);
        DeployedMessageContract producerOne = contract("producer-one", MessageContractRole.PRODUCER);
        DeployedMessageContract producerTwo = contract("producer-two", MessageContractRole.PRODUCER);
        DeployedMessageContract wrongTopicProducer = contract("wrong-topic", MessageContractRole.PRODUCER, "other-topic");
        DeployedMessageContract sameRole = contract("same-role", MessageContractRole.CONSUMER);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(requestingConsumer, producerOne, producerTwo, wrongTopicProducer, sameRole));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(mock(SchemaIncompatibility.class)));

        assertThat(service.findCompatibleReleasesForApp("requesting", PACKAGE_NAME, "1.0.0", "PROD"))
                .isEmpty();
        ArgumentCaptor<MessageContract> readers = ArgumentCaptor.forClass(MessageContract.class);
        ArgumentCaptor<MessageContract> writers = ArgumentCaptor.forClass(MessageContract.class);
        verify(compatibilityService, times(2)).validateReaderWriterCompatibility(readers.capture(), writers.capture());
        assertThat(readers.getAllValues()).allMatch(reader -> reader.getMessageTypeVersion().equals("1.1.0"));
        assertThat(writers.getAllValues()).extracting(MessageContract::getAppName)
                .containsExactly("producer-one", "producer-two");
    }

    @Test
    void requestingProducerUsesDeployedConsumerAsReaderAndCandidateAsWriter() {
        DeployedMessageContract requestingProducer = contract("requesting", MessageContractRole.PRODUCER);
        DeployedMessageContract deployedConsumer = contract("consumer", MessageContractRole.CONSUMER);
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(requestingProducer, deployedConsumer));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        assertThat(service.findCompatibleReleasesForApp("requesting", PACKAGE_NAME, "1.0.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.1.0");
        ArgumentCaptor<MessageContract> reader = ArgumentCaptor.forClass(MessageContract.class);
        ArgumentCaptor<MessageContract> writer = ArgumentCaptor.forClass(MessageContract.class);
        verify(compatibilityService).validateReaderWriterCompatibility(reader.capture(), writer.capture());
        assertThat(reader.getValue().getAppName()).isEqualTo("consumer");
        assertThat(writer.getValue().getMessageTypeVersion()).isEqualTo("1.1.0");
    }

    @Test
    void repeatedRequestsForSameMessageTypeAndEnvironmentAreServedFromCacheWithinTtl() {
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
        clock.advance(Duration.ofMinutes(4));
        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "0.9.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.0.0", "1.1.0");

        verify(contractRepository, times(1)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
        verify(repositoryFactory, times(1)).cloneRepository("registry-url");
        verify(typeRepository, times(1)).getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ");
        // the candidate schema is loaded once per candidate version: 1.1.0, and 1.0.0 for the last request
        verify(schemaService, times(2)).loadSchemas(anyList());
        // global mode compares with all deployed contracts, app-specific mode only with the counterparts
        verify(contractRepository).findSchemas(Set.of(requestingConsumer.id(), deployedProducer.id()));
        verify(contractRepository).findSchemas(Set.of(deployedProducer.id()));
        ArgumentCaptor<MessageContract> writers = ArgumentCaptor.forClass(MessageContract.class);
        verify(compatibilityService, atLeastOnce()).validateReaderWriterCompatibility(any(), writers.capture());
        assertThat(writers.getAllValues())
                .filteredOn(writer -> writer.getAppName().equals("producer"))
                .isNotEmpty()
                .allMatch(writer -> "producer-protocol".equals(writer.getAvroProtocolSchema()));
    }

    @Test
    void requestsAreAnsweredFromCurrentDataAfterTtlExpired() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        givenDeployedContractsAndRegistry(deployedProducer);
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        clock.advance(Duration.ofMinutes(5));
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
    void registryFailureIsNotCached() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        MessageTypeRepository typeRepository = givenDeployedContractsAndRegistry(deployedProducer);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenThrow(new IllegalStateException("registry down"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.1.0")));
        when(compatibilityService.validateReaderWriterCompatibility(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .isInstanceOf(RenovateRegistryException.class);
        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD"))
                .extracting(RenovateRelease::version).containsExactly("1.1.0");
    }

    @Test
    void schemasOfDeployedContractsAreNotLoadedWithoutNewerCandidate() {
        DeployedMessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
        givenDeployedContractsAndRegistry(deployedProducer);

        assertThat(service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.1.0", "PROD")).isEmpty();

        verify(contractRepository, never()).findSchemas(anyCollection());
        verifyNoInteractions(schemaService, compatibilityService);
    }

    @Test
    void cachingIsDisabledWithZeroTtl() {
        RenovateProperties properties = new RenovateProperties();
        properties.setCacheTtl(Duration.ZERO);
        service = new RenovateCompatibilityService(contractRepository, repositoryFactory, schemaService,
                compatibilityService, properties, clock);
        when(contractRepository.findCurrentlyDeployedContracts(anyString(), anyString())).thenReturn(List.of());

        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");
        service.findGloballyCompatibleReleases(PACKAGE_NAME, "1.0.0", "PROD");

        verify(contractRepository, times(2)).findCurrentlyDeployedContracts("PROD", "activzoneenteredevent");
    }

    private MessageTypeRepository givenDeployedContractsAndRegistry(DeployedMessageContract... deployedContracts) {
        MessageTypeRepository typeRepository = mock(MessageTypeRepository.class);
        when(contractRepository.findCurrentlyDeployedContracts("PROD", "activzoneenteredevent"))
                .thenReturn(List.of(deployedContracts));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(typeRepository);
        when(typeRepository.getMessageTypeSnapshot("master", "ActivZoneEnteredEvent", "activ"))
                .thenReturn(new MessageTypeRepository.MessageTypeSnapshot(SNAPSHOT_COMMIT, List.of("1.0.0", "1.1.0")));
        return typeRepository;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T02:59:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static DeployedMessageContract contract(MessageContractRole role) {
        return contract("app", role);
    }

    private static DeployedMessageContract contract(String appName, MessageContractRole role) {
        return contract(appName, role, "topic");
    }

    private static DeployedMessageContract contract(String appName, MessageContractRole role, String topic) {
        return new DeployedMessageContract(UUID.randomUUID(), appName, "1.0.0", "ActivZoneEnteredEvent", "1.0.0",
                topic, role, "registry-url", "master", CompatibilityMode.NONE, null);
    }
}
