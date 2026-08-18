package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepoException;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractInfo;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
                .satisfies(status -> assertThat(status.upToDate()).isTrue());
    }

    @Test
    void skipsRegistryInfrastructureFailure() {
        MessageContractInfo contract = mock(MessageContractInfo.class);
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url"))
                .thenThrow(MessageTypeRepoException.checkoutFailed("master", null, new IllegalStateException()));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD")).isEmpty();
    }

    @Test
    void skipsUnexpectedRegistryRuntimeFailure() {
        MessageContractInfo contract = mock(MessageContractInfo.class);
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenThrow(new IllegalStateException("clone failed"));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD")).isEmpty();
    }

    @Test
    void returnsStatusesFromAvailableRegistriesWhenAnotherRegistryFails() {
        MessageContractInfo availableContract = contract("test-service", "TestEvent", "1.9.0");
        MessageContractInfo unavailableContract = mock(MessageContractInfo.class);
        when(unavailableContract.getRegistryUrl()).thenReturn("unavailable-registry");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD"))
                .thenReturn(List.of(availableContract, unavailableContract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(repositoryFactory.cloneRepository("unavailable-registry"))
                .thenThrow(new IllegalStateException("clone failed"));
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("TestEvent")))
                .thenReturn(Map.of("TestEvent", List.of("1.9.0", "1.10.0")));

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD"))
                .singleElement()
                .satisfies(status -> assertThat(status.appName()).isEqualTo("test-service"));
    }

    @Test
    void skipsContractMissingFromDefaultBranch() {
        MessageContractInfo contract = mock(MessageContractInfo.class);
        when(contract.getRegistryUrl()).thenReturn("registry-url");
        when(contract.getMessageType()).thenReturn("MissingEvent");
        when(contract.getAppName()).thenReturn("test-service");
        when(contract.getAppVersion()).thenReturn("3.0.0");
        when(contractRepository.findMessageContractInfosByEnvironment("PROD")).thenReturn(List.of(contract));
        when(repositoryFactory.cloneRepository("registry-url")).thenReturn(messageTypeRepository);
        when(messageTypeRepository.getMessageTypeVersionsFromDefaultBranch(java.util.Set.of("MissingEvent")))
                .thenReturn(Map.of());

        MessageContractVersionService service = new MessageContractVersionService(contractRepository, repositoryFactory);

        assertThat(service.getVersionStatus("PROD")).isEmpty();
    }

    private static MessageContractInfo contract(String appName, String messageType, String version) {
        MessageContractInfo contract = mock(MessageContractInfo.class);
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
