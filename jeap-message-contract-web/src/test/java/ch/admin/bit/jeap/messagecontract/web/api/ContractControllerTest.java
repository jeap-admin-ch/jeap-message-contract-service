package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import ch.admin.bit.jeap.messagecontract.web.api.dto.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractControllerTest extends ControllerTestBase {

    private static final String API_CONTRACTS = "/api/contracts";
    private static final String API_CONTRACTS_APP_VERSION = "/api/contracts/{appName}/{appVersion}";
    private static final String API_DEPLOYMENTS_APP_ENV = "/api/deployments/{appName}/{appVersion}/{environment}";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SECRET = "secret";
    private static final String WRITE = "write";
    private static final String TEST_TOPIC = "test-topic";
    private static final String TOPIC = "topic";
    private static final String VERSION_1_0_0 = "1.0.0";
    private static final String VERSION_2_0_0 = "2.0.0";
    private static final String VERSION_3_0_0 = "3.0.0";
    private static final String ACTIV_ZONE_ENTERED_EVENT = "ActivZoneEnteredEvent";
    private static final String MY_APP_NAME = "myAppName";
    private static final String VERSION1 = "version1";
    private static final String VERSION2 = "version2";
    private static final String TEST_KEY_ID = "testKeyId";
    private static final String MASTER = "master";

    private static TestRegistryRepo repo;
    private static String repoUrl;

    private final JpaMessageContractRepository messageContractRepository;
    private final JpaDeploymentRepository deploymentRepository;
    private final JsonMapper jsonMapper;

    @Autowired
    ContractControllerTest(MockMvc mockMvc,
                            JpaMessageContractRepository messageContractRepository,
                            JpaDeploymentRepository deploymentRepository,
                            JsonMapper jsonMapper) {
        super(mockMvc);
        this.messageContractRepository = messageContractRepository;
        this.deploymentRepository = deploymentRepository;
        this.jsonMapper = jsonMapper;
    }

    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    @BeforeAll
    static void prepareRepository() throws Exception {
        repo = TestRegistryRepo.createMessageTypeRegistryRepository();
        repoUrl = repo.url();
    }

    @AfterAll
    static void deleteRepository() throws Exception {
        repo.delete();
    }

    @AfterEach
    void cleanup() {
        messageContractRepository.deleteAll();
        deploymentRepository.deleteAll();
    }

    @Test
    @SneakyThrows
    void getContracts() {
        mockMvc.perform(get(API_CONTRACTS))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    void putContractsUnauthorized() {
        mockMvc.perform(put(API_CONTRACTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void putContractUploadRoleSuccess() {
        MessageContractDto contract1 = new MessageContractDto("app2", VERSION_3_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TEST_TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", VERSION_3_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_2_0_0,
                TEST_TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        mockMvc.perform(put(API_CONTRACTS_APP_VERSION, "app", VERSION_1_0_0)
                        .header(AUTHORIZATION, basicAuth("upload", SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @Test
    @SneakyThrows
    void putThenGetContracts() {
        MessageContractDto contract1 = new MessageContractDto("app", VERSION_2_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app", VERSION_2_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        putContracts("app", VERSION_2_0_0, messageContractsDto);

        MvcResult result = mockMvc.perform(get(API_CONTRACTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(List.of(contract1, contract2), dtos);
    }

    @Test
    @SneakyThrows
    void putThenGetContractsWithLegacyCreateContractsDto() {
        MessageContractDto contract = new MessageContractDto("app", VERSION_1_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, null);

        LegacyCreateMessageContractsDto legacyCreateMessageContractsDto = new LegacyCreateMessageContractsDto(List.of(createLegacyNew(contract)));

        putLegacyContracts("app", VERSION_1_0_0, legacyCreateMessageContractsDto);

        MvcResult result = mockMvc.perform(get(API_CONTRACTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(List.of(contract), dtos);
    }

    @Test
    @SneakyThrows
    void putThenReplaceContracts() {
        NewMessageContractDto contract1 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        NewMessageContractDto contract2 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        List<NewMessageContractDto> uploadedContracts = List.of(contract1, contract2);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);
        putContracts("app", VERSION_2_0_0, messageContractsDto);

        assertContractsCount(2);

        List<NewMessageContractDto> replacedContracts = List.of(contract1);
        CreateMessageContractsDto replacedContractsDto = new CreateMessageContractsDto(replacedContracts);
        putContracts("app", VERSION_2_0_0, replacedContractsDto);

        assertContractsCount(1);
    }

    @SneakyThrows
    private void assertContractsCount(int expected) {
        MvcResult result = mockMvc.perform(get(API_CONTRACTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(expected, dtos.size());
    }

    @Test
    @SneakyThrows
    void putThenAddContractsForSameTransaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(2);
    }

    @Test
    @SneakyThrows
    void putThenAddSameContractForSameTransaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);
        MvcResult result;
        List<MessageContractDto> dtos;

        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract1)));

        result = mockMvc.perform(get(API_CONTRACTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        dtos = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(1, dtos.size());
    }

    @Test
    @SneakyThrows
    void putThenAddContractsForOtherTransaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        putContracts("app", VERSION_2_0_0, "tx2", new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(1);
    }

    @Test
    void putThenAddContractsForOtherNullTransaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", VERSION_2_0_0, "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        putContracts("app", VERSION_2_0_0, new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(1);
    }


    @Test
    @SneakyThrows
    void putInvalidContract() {
        NewMessageContractDto contract = new NewMessageContractDto("", "",
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        List<NewMessageContractDto> uploadedContracts = List.of(contract);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);

        mockMvc.perform(put("/api/contracts/app/2.0.0")
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isBadRequest()); // 400
    }

    @Test
    @SneakyThrows
    void deleteContractUnauthorized() {
        mockMvc.perform(delete("/api/contracts/app/1"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void putThenDeleteContract() {
        MessageContractDto contract1 = new MessageContractDto("app2", VERSION_3_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TEST_TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", VERSION_3_0_0, ACTIV_ZONE_ENTERED_EVENT, VERSION_2_0_0,
                TEST_TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        // given: two uploaded contracts
        putContracts("app2", VERSION_3_0_0, messageContractsDto);

        // when: deleting contract 2
        mockMvc.perform(delete("/api/contracts/app2/3.0.0")
                        .param("messageType", ACTIV_ZONE_ENTERED_EVENT)
                        .param("messageTypeVersion", VERSION_2_0_0)
                        .param("topic", TEST_TOPIC)
                        .param("role", "PRODUCER")
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET)))
                .andExpect(status().is2xxSuccessful());

        // then: expect only contract 1 to exist
        MvcResult result = mockMvc.perform(get(API_CONTRACTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        List<MessageContractDto> dtos = jsonMapper.readValue(
                responseBody,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(dtos.contains(contract1));
        assertFalse(dtos.contains(contract2));
    }

    @Test
    @SneakyThrows
    void deleteContractUploadRoleForbidden() {
        mockMvc.perform(delete("/api/contracts/app2/3.0.0")
                        .param("messageType", ACTIV_ZONE_ENTERED_EVENT)
                        .param("messageTypeVersion", VERSION_2_0_0)
                        .param("topic", TEST_TOPIC)
                        .param("role", "PRODUCER")
                        .header(AUTHORIZATION, basicAuth("upload", SECRET)))
                .andExpect(status().isForbidden()); // 403
    }

    @Test
    @SneakyThrows
    void putThenGetByEnvironment() {
        MessageContractDto contract1 = new MessageContractDto(MY_APP_NAME, VERSION1, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto(MY_APP_NAME, VERSION1, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);
        MessageContractDto contract3 = new MessageContractDto(MY_APP_NAME, VERSION2, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0,
                TOPIC, MessageContractRole.PRODUCER, repoUrl, null, MASTER, CompatibilityMode.FORWARD, TEST_KEY_ID);

        putContracts(MY_APP_NAME, VERSION1, new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2))));
        putContracts(MY_APP_NAME, VERSION2, new CreateMessageContractsDto(singletonList(createNew(contract3))));

        // put deployment v1 - PROD
        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV, MY_APP_NAME, VERSION1, "prod")
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET)))
                .andExpect(status().isCreated()); // 201


        // GET /api/contracts?env=prod
        MvcResult prodResult = mockMvc.perform(get(API_CONTRACTS)
                        .param("env", "prod")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> prodDtos = jsonMapper.readValue(
                prodResult.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(prodDtos.contains(contract1));
        assertTrue(prodDtos.contains(contract2));
        assertFalse(prodDtos.contains(contract3));

        // GET /api/contracts?env=ref (should be empty)
        MvcResult refResult = mockMvc.perform(get(API_CONTRACTS)
                        .param("env", "ref")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> refDtos = jsonMapper.readValue(
                refResult.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(refDtos.isEmpty());

        // PUT /api/deployments/{appName}/{appVersion}/{environment} for version2/ref
        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV, MY_APP_NAME, VERSION2, "ref")
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET)))
                .andExpect(status().isCreated()); // 201

        // GET /api/contracts?env=ref (should now contain contract3)
        MvcResult refResultAfter = mockMvc.perform(get(API_CONTRACTS)
                        .param("env", "ref")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> refDtosAfter = jsonMapper.readValue(
                refResultAfter.getResponse().getContentAsString(),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertFalse(refDtosAfter.contains(contract1));
        assertFalse(refDtosAfter.contains(contract2));
        assertTrue(refDtosAfter.contains(contract3));
    }

    private NewMessageContractDto createNew(MessageContractDto contractDto) {
        return new NewMessageContractDto(contractDto.messageType(),
                contractDto.messageTypeVersion(),
                contractDto.topic(),
                contractDto.role(),
                contractDto.registryUrl(),
                contractDto.commitHash(),
                contractDto.branch(),
                contractDto.compatibilityMode(),
                contractDto.encryptionKeyId()
        );
    }

    private LegacyNewMessageContractDto createLegacyNew(MessageContractDto contractDto) {
        return new LegacyNewMessageContractDto(contractDto.messageType(),
                contractDto.messageTypeVersion(),
                contractDto.topic(),
                contractDto.role(),
                contractDto.registryUrl(),
                contractDto.commitHash(),
                contractDto.branch(),
                contractDto.compatibilityMode()
        );
    }

    @SneakyThrows
    private void putContracts(String appName, String appVersion, String transactionId, CreateMessageContractsDto messageContractsDto) {
        mockMvc.perform(put(API_CONTRACTS_APP_VERSION, appName, appVersion)
                        .param("transactionId", transactionId)
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    private void putContracts(String appName, String appVersion, CreateMessageContractsDto messageContractsDto) {
        mockMvc.perform(put(API_CONTRACTS_APP_VERSION, appName, appVersion)
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    private void putLegacyContracts(String appName, String appVersion, LegacyCreateMessageContractsDto legacyMessageContractsDto) {
        mockMvc.perform(put(API_CONTRACTS_APP_VERSION, appName, appVersion)
                        .header(AUTHORIZATION, basicAuth(WRITE, SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(legacyMessageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

}
