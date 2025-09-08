package ch.admin.bit.jeap.messagecontract.domain.schema;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryProperties;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MessageSchemaServiceTest {

    private static TestRegistryRepo testRepo;
    private MessageSchemaService messageSchemaService;

    @BeforeAll
    static void prepareRepository() throws Exception {
        testRepo = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterAll
    static void deleteRepository() throws Exception {
        testRepo.delete();
    }

    @BeforeEach
    void setup() {
        messageSchemaService = new MessageSchemaService(new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties()));
    }

    @Test
    void loadSchemas() {
        MessageContract contract = createContract(testRepo.url());

        messageSchemaService.loadSchemas(List.of(contract));

        assertThat(contract.getAvroProtocolSchema())
                .isNotEmpty();
    }

    @SuppressWarnings("resource")
    @Test
    void loadSchemas_whenMultipleContractsForSameRegistryAreUploaded_thenShouldOnlyCloneRepositoryOnce() {
        String repoUrl1 = "repoUrl1";
        String repoUrl2 = "repoUrl2";
        MessageContract contract1 = createContract(repoUrl1);
        MessageContract contract2 = createContract(repoUrl1);
        MessageContract contract3 = createContract(repoUrl2);
        MessageContract contract4 = createContract(repoUrl2);
        MessageTypeRepositoryFactory repoFactory = mock(MessageTypeRepositoryFactory.class);
        MessageTypeRepository repo = mock(MessageTypeRepository.class);
        when(repoFactory.cloneRepository(anyString())).thenReturn(repo);
        MessageSchemaService messageSchemaService = new MessageSchemaService(repoFactory);

        messageSchemaService.loadSchemas(List.of(contract1, contract2, contract3, contract4));

        verify(repoFactory, times(1)).cloneRepository(repoUrl1);
        verify(repoFactory, times(1)).cloneRepository(repoUrl2);
    }

    private MessageContract createContract(String repoUrl) {
        return MessageContract.builder()
                .appName("app")
                .appVersion("1")
                .messageType("ActivZoneEnteredEvent")
                .messageTypeVersion("1.0.0")
                .topic("topic")
                .role(MessageContractRole.CONSUMER)
                .registryUrl(repoUrl)
                .commitHash(testRepo.revision())
                .branch("master")
                .compatibilityMode(CompatibilityMode.BACKWARD)
                .build();
    }
}
