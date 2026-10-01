package ch.admin.bit.jeap.messagecontract.domain.schema;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryProperties;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MessageSchemaServiceTest {

    private static final String ACTIV_ZONE_ENTERED_EVENT = "ActivZoneEnteredEvent";
    private static final String VERSION_1_0_0 = "1.0.0";
    private static final String TOPIC = "topic";
    private static final String MASTER = "master";
    private static final String SCHEMA_JSON = """
            {"type":"record","name":"ActivZoneEnteredEvent","namespace":"ch.admin.bit.jeap.activ",
             "fields":[{"name":"zoneId","type":"string"}]}
            """;

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
        messageSchemaService = new MessageSchemaService(new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry()));
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
    void loadSchemasWhenMultipleContractsForSameRegistryAreUploadedThenShouldOnlyCloneRepositoryOnce() {
        String repoUrl1 = "repoUrl1";
        String repoUrl2 = "repoUrl2";
        MessageContract contract1 = createContract(repoUrl1);
        MessageContract contract2 = createContract(repoUrl1);
        MessageContract contract3 = createContract(repoUrl2);
        MessageContract contract4 = createContract(repoUrl2);
        MessageTypeRepositoryFactory repoFactory = mock(MessageTypeRepositoryFactory.class);
        MessageTypeRepository repo = mock(MessageTypeRepository.class);
        when(repoFactory.cloneRepository(anyString())).thenReturn(repo);
        MessageSchemaService localMessageSchemaService = new MessageSchemaService(repoFactory);

        localMessageSchemaService.loadSchemas(List.of(contract1, contract2, contract3, contract4));

        verify(repoFactory, times(1)).cloneRepository(repoUrl1);
        verify(repoFactory, times(1)).cloneRepository(repoUrl2);
    }

    @Test
    void loadSchemas_whenSchemaProvidedInUpload_thenDoNotAccessRegistry() {
        MessageContract contract = createContractWithSchema("some-unreachable-registry-url", SCHEMA_JSON);
        MessageTypeRepositoryFactory repoFactory = mock(MessageTypeRepositoryFactory.class);
        MessageSchemaService localMessageSchemaService = new MessageSchemaService(repoFactory);

        localMessageSchemaService.loadSchemas(List.of(contract));

        verifyNoInteractions(repoFactory);
        assertThat(contract.getAvroSchema()).isEqualTo(SCHEMA_JSON);
        assertThat(contract.getAvroProtocolSchema()).isNull();
    }

    @SuppressWarnings("resource")
    @Test
    void loadSchemas_whenSchemaProvidedForSomeContractsOnly_thenLoadMissingSchemasFromRegistry() {
        MessageContract contractWithSchema = createContractWithSchema(testRepo.url(), SCHEMA_JSON);
        MessageContract contractWithoutSchema = createContract(testRepo.url());

        messageSchemaService.loadSchemas(List.of(contractWithSchema, contractWithoutSchema));

        assertThat(contractWithSchema.getAvroSchema()).isEqualTo(SCHEMA_JSON);
        assertThat(contractWithSchema.getAvroProtocolSchema()).isNull();
        assertThat(contractWithoutSchema.getAvroSchema()).isNull();
        assertThat(contractWithoutSchema.getAvroProtocolSchema()).isNotEmpty();
    }

    @Test
    void loadSchemas_whenUploadedSchemaIsNotValidAvro_thenThrowsIllegalArgumentException() {
        MessageContract contract = createContractWithSchema(testRepo.url(), "this is not a schema");

        List<MessageContract> contracts = List.of(contract);
        assertThatThrownBy(() -> messageSchemaService.loadSchemas(contracts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ACTIV_ZONE_ENTERED_EVENT);
    }

    @Test
    void loadSchemas_whenUploadedSchemaIsForAnotherMessageType_thenThrowsIllegalArgumentException() {
        MessageContract contract = createContractWithSchema(testRepo.url(), SCHEMA_JSON.replace(ACTIV_ZONE_ENTERED_EVENT, "SomeOtherEvent"));

        List<MessageContract> contracts = List.of(contract);
        assertThatThrownBy(() -> messageSchemaService.loadSchemas(contracts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ACTIV_ZONE_ENTERED_EVENT)
                .hasMessageContaining("SomeOtherEvent");
    }

    @Test
    void loadSchemas_whenUploadedSchemaIsNotARecord_thenThrowsIllegalArgumentException() {
        MessageContract contract = createContractWithSchema(testRepo.url(), "\"string\"");

        List<MessageContract> contracts = List.of(contract);
        assertThatThrownBy(() -> messageSchemaService.loadSchemas(contracts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ACTIV_ZONE_ENTERED_EVENT);
    }

    @Test
    void loadSchemas_whenUploadedSchemaIsBlank_thenLoadSchemaFromRegistry() {
        MessageContract contract = createContractWithSchema(testRepo.url(), "  ");

        messageSchemaService.loadSchemas(List.of(contract));

        assertThat(contract.getAvroProtocolSchema()).isNotEmpty();
    }

    private MessageContract createContract(String repoUrl) {
        return contractBuilder(repoUrl)
                .build();
    }

    private MessageContract createContractWithSchema(String repoUrl, String schema) {
        return contractBuilder(repoUrl)
                .avroSchema(schema)
                .build();
    }

    private MessageContract.MessageContractBuilder contractBuilder(String repoUrl) {
        return MessageContract.builder()
                .appName("app")
                .appVersion("1")
                .messageType(ACTIV_ZONE_ENTERED_EVENT)
                .messageTypeVersion(VERSION_1_0_0)
                .topic(TOPIC)
                .role(MessageContractRole.CONSUMER)
                .registryUrl(repoUrl)
                .commitHash(testRepo.revision())
                .branch(MASTER)
                .compatibilityMode(CompatibilityMode.BACKWARD);
    }
}
