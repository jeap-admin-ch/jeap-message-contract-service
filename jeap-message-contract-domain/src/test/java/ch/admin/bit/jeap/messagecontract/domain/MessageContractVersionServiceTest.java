package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageContractVersionServiceTest {

    @Mock
    private MessageContractRepository contractRepository;
    @Mock
    private MessageTypeRepositoryFactory repositoryFactory;
    @Mock
    private MessageTypeRepository messageTypeRepository;

    @Test
    void usesRegistryDefaultBranchRegardlessOfContractBranch() {
        MessageContractInfo contract = contract("test-service", "TestEvent", "1.9.0");
        lenient().when(contract.getBranch()).thenReturn("story/deleted-feature-branch");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("1.10.0", "1.9.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("prod"))
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.usedVersion()).isEqualTo("1.9.0");
                    assertThat(status.latestVersion()).isEqualTo("1.10.0");
                    assertThat(status.upToDate()).isFalse();
                });
        verify(repositoryFactory).cloneRepository("registry-url");
        verify(contract, never()).getBranch();
    }

    @Test
    void reportsUpToDateContract() {
        MessageContractInfo contract = contract("test-service", "TestEvent", "1.10.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("1.9.0", "1.10.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD")).singleElement()
                .satisfies(status -> {
                    assertThat(status.upToDate()).isTrue();
                    assertThat(status.state()).isEqualTo(MessageContractVersionState.UP_TO_DATE);
                });
    }

    @Test
    void failsOnRegistryInfrastructureFailure() {
        MessageContractInfo contract = mock(MessageContractInfo.class);
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url"))
                .thenThrow(MessageTypeRepoException.checkoutFailed("master", null, new IllegalStateException()));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThatThrownBy(() -> service.getVersionStatus("PROD"))
                .isInstanceOf(MessageContractVersionStatusUnavailableException.class);
    }

    @Test
    void failsOnUnexpectedRegistryRuntimeFailure() {
        MessageContractInfo contract = mock(MessageContractInfo.class);
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenThrow(new IllegalStateException("clone failed"));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThatThrownBy(() -> service.getVersionStatus("PROD"))
                .isInstanceOf(MessageContractVersionStatusUnavailableException.class);
    }

    @Test
    void rejectsPartialResultsWhenAnotherRegistryFails() {
        MessageContractInfo availableContract = contract("test-service", "TestEvent", "1.9.0");
        MessageContractInfo unavailableContract = mock(MessageContractInfo.class);
        when(unavailableContract.getRegistryUrl()).thenReturn("unavailable-registry");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD"))
                .thenReturn(List.of(availableContract, unavailableContract));
        lenient().when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(repositoryFactory.cloneRepository("unavailable-registry"))
                .thenThrow(new IllegalStateException("clone failed"));
        lenient().when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("1.9.0", "1.10.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThatThrownBy(() -> service.getVersionStatus("PROD"))
                .isInstanceOf(MessageContractVersionStatusUnavailableException.class);
    }

    @Test
    void returnsUnknownForContractMissingFromDefaultBranch() {
        MessageContractInfo contract = contract("test-service", "MissingEvent", "1.0.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("MissingEvent")))
                .thenReturn(Map.of());

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .singleElement()
                .satisfies(status -> assertUnknown(status, "test-service", "MissingEvent", "1.0.0"));
    }

    @Test
    void returnsUnknownForUnresolvableContractAndResolvesAllOthersOfTheSameRegistry() {
        MessageContractInfo outdatedContract = contract("a-service", "KnownEvent", "1.0.0");
        MessageContractInfo missingContract = contract("b-service", "MissingEvent", "2.0.0");
        MessageContractInfo upToDateContract = contract("c-service", "OtherEvent", "3.1.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD"))
                .thenReturn(List.of(missingContract, outdatedContract, upToDateContract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(
                java.util.Set.of("KnownEvent", "MissingEvent", "OtherEvent")))
                .thenReturn(Map.of(
                        "KnownEvent", List.of("1.0.0", "1.1.0"),
                        "OtherEvent", List.of("3.0.0", "3.1.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        List<MessageContractVersionStatus> statuses = service.getVersionStatus("PROD");

        assertThat(statuses).hasSize(3);
        assertThat(statuses.get(0)).satisfies(status -> {
            assertThat(status.appName()).isEqualTo("a-service");
            assertThat(status.latestVersion()).isEqualTo("1.1.0");
            assertThat(status.state()).isEqualTo(MessageContractVersionState.OUTDATED);
            assertThat(status.upToDate()).isFalse();
        });
        assertThat(statuses.get(1)).satisfies(status -> assertUnknown(status, "b-service", "MissingEvent", "2.0.0"));
        assertThat(statuses.get(2)).satisfies(status -> {
            assertThat(status.appName()).isEqualTo("c-service");
            assertThat(status.latestVersion()).isEqualTo("3.1.0");
            assertThat(status.state()).isEqualTo(MessageContractVersionState.UP_TO_DATE);
            assertThat(status.upToDate()).isTrue();
        });
    }

    @Test
    void returnsUnknownForAllUnresolvableContractsInsteadOfStoppingAtTheFirst() {
        MessageContractInfo firstMissing = contract("a-service", "FirstMissingEvent", "1.0.0");
        MessageContractInfo secondMissing = contract("b-service", "SecondMissingEvent", "1.0.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD"))
                .thenReturn(List.of(firstMissing, secondMissing));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(
                java.util.Set.of("FirstMissingEvent", "SecondMissingEvent")))
                .thenReturn(Map.of("SecondMissingEvent", List.of()));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .extracting(MessageContractVersionStatus::state)
                .containsExactly(MessageContractVersionState.UNKNOWN, MessageContractVersionState.UNKNOWN);
    }

    @Test
    void returnsEmptyCompleteResultWithoutAccessingRegistriesWhenNoContractsAreDeployed() {
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of());
        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("prod")).isEmpty();
        verifyNoInteractions(repositoryFactory);
    }

    @Test
    void returnsUnknownWhenRegistryUrlIsMissing() {
        MessageContractInfo contract = contract("test-service", "TestEvent", "1.0.0");
        when(contract.getRegistryUrl()).thenReturn(null);
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .singleElement()
                .satisfies(status -> assertUnknown(status, "test-service", "TestEvent", "1.0.0"));
        verifyNoInteractions(repositoryFactory);
    }

    @Test
    void returnsUnknownWhenRegistryReturnsInvalidVersionData() {
        MessageContractInfo contract = contract("test-service", "TestEvent", "1.0.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("invalid")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .singleElement()
                .satisfies(status -> assertUnknown(status, "test-service", "TestEvent", "1.0.0"));
    }

    @Test
    void returnsUnknownWhenUsedVersionIsInvalid() {
        MessageContractInfo contract = contract("test-service", "TestEvent", "not-a-version");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("1.0.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .singleElement()
                .satisfies(status -> assertUnknown(status, "test-service", "TestEvent", "not-a-version"));
    }

    private static void assertUnknown(MessageContractVersionStatus status, String appName, String messageType,
                                      String usedVersion) {
        assertThat(status.appName()).isEqualTo(appName);
        assertThat(status.appVersion()).isEqualTo("3.0.0");
        assertThat(status.messageType()).isEqualTo(messageType);
        assertThat(status.usedVersion()).isEqualTo(usedVersion);
        assertThat(status.latestVersion()).isNull();
        assertThat(status.topic()).isEqualTo("test-topic");
        assertThat(status.role()).isEqualTo(MessageContractRole.CONSUMER);
        assertThat(status.state()).isEqualTo(MessageContractVersionState.UNKNOWN);
        assertThat(status.upToDate()).isFalse();
    }

    private static MessageContractInfo contract(String appName, String messageType, String version) {
        MessageContractInfo contract = mock(MessageContractInfo.class,
                withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        when(contract.getAppName()).thenReturn(appName);
        when(contract.getAppVersion()).thenReturn("3.0.0");
        when(contract.getMessageType()).thenReturn(messageType);
        when(contract.getMessageTypeVersion()).thenReturn(version);
        when(contract.getTopic()).thenReturn("test-topic");
        when(contract.getRole()).thenReturn("CONSUMER");
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        return contract;
    }
}
