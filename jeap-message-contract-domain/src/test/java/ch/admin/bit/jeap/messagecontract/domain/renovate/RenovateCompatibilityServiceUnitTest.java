package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaCompatibilityService;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.SchemaIncompatibility;
import ch.admin.bit.jeap.messagecontract.domain.schema.MessageSchemaService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
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
    private RenovateCompatibilityService service;

    @BeforeEach
    void setUp() {
        service = new RenovateCompatibilityService(contractRepository, repositoryFactory, schemaService,
                compatibilityService);
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
                Arguments.of(PACKAGE_NAME, "1.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "-1.0.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "01.0.0", "PROD"),
                Arguments.of(PACKAGE_NAME, "1.0.0", " "));
    }

    @Test
    void candidateSchemaIsLoadedFromEnumerationSnapshotCommit() {
        MessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
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
        MessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
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
        MessageContract deployedProducer = contract(MessageContractRole.PRODUCER);
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
        MessageContract requestingConsumer = contract("requesting", MessageContractRole.CONSUMER);
        MessageContract producerOne = contract("producer-one", MessageContractRole.PRODUCER);
        MessageContract producerTwo = contract("producer-two", MessageContractRole.PRODUCER);
        MessageContract wrongTopicProducer = contract("wrong-topic", MessageContractRole.PRODUCER, "other-topic");
        MessageContract sameRole = contract("same-role", MessageContractRole.CONSUMER);
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
        MessageContract requestingProducer = contract("requesting", MessageContractRole.PRODUCER);
        MessageContract deployedConsumer = contract("consumer", MessageContractRole.CONSUMER);
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

    private static MessageContract contract(MessageContractRole role) {
        return contract("app", role);
    }

    private static MessageContract contract(String appName, MessageContractRole role) {
        return contract(appName, role, "topic");
    }

    private static MessageContract contract(String appName, MessageContractRole role, String topic) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion("1.0.0")
                .messageType("ActivZoneEnteredEvent")
                .messageTypeVersion("1.0.0")
                .topic(topic)
                .role(role)
                .registryUrl("registry-url")
                .branch("master")
                .compatibilityMode(CompatibilityMode.NONE)
                .build();
    }
}
