package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import ch.admin.bit.jeap.messagecontract.web.api.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractControllerTest extends ControllerTestBase {

    private static TestRegistryRepo repo;
    private static String repoUrl;

    @Autowired
    private JpaMessageContractRepository messageContractRepository;

    @Autowired
    private JpaDeploymentRepository deploymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    void get_contracts() {
        mockMvc.perform(get("/api/contracts"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    void put_contracts_unauthorized() {
        mockMvc.perform(put("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void putContract_uploadRole_success() {
        MessageContractDto contract1 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "test-topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "2.0.0",
                "test-topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", "app", "1.0.0")
                        .header("Authorization", basicAuth("upload", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @Test
    @SneakyThrows
    void put_then_get_contracts() {
        MessageContractDto contract1 = new MessageContractDto("app", "2.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app", "2.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        putContracts("app", "2.0.0", messageContractsDto);

        MvcResult result = mockMvc.perform(get("/api/contracts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(List.of(contract1, contract2), dtos);
    }

    @Test
    @SneakyThrows
    void put_then_get_contracts_with_legacy_create_contracts_dto() {
        MessageContractDto contract = new MessageContractDto("app", "1.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, null);

        LegacyCreateMessageContractsDto legacyCreateMessageContractsDto = new LegacyCreateMessageContractsDto(List.of(createLegacyNew(contract)));

        putLegacyContracts("app", "1.0.0", legacyCreateMessageContractsDto);

        MvcResult result = mockMvc.perform(get("/api/contracts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(List.of(contract), dtos);
    }

    @Test
    @SneakyThrows
    void put_then_replace_contracts() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        List<NewMessageContractDto> uploadedContracts = List.of(contract1, contract2);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);
        putContracts("app", "2.0.0", messageContractsDto);

        assertContractsCount(2);

        List<NewMessageContractDto> replacedContracts = List.of(contract1);
        CreateMessageContractsDto replacedContractsDto = new CreateMessageContractsDto(replacedContracts);
        putContracts("app", "2.0.0", replacedContractsDto);

        assertContractsCount(1);
    }

    @SneakyThrows
    private void assertContractsCount(int expected) {
        MvcResult result = mockMvc.perform(get("/api/contracts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> dtos = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(expected, dtos.size());
    }

    @Test
    @SneakyThrows
    void put_then_add_contracts_for_same_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(2);
    }

    @Test
    @SneakyThrows
    void put_then_add_same_contract_for_same_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);
        MvcResult result;
        List<MessageContractDto> dtos;

        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        result = mockMvc.perform(get("/api/contracts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        dtos = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertEquals(1, dtos.size());
    }

    @Test
    @SneakyThrows
    void put_then_add_contracts_for_other_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", "tx2", new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(1);
    }

    @Test
    void put_then_add_contracts_for_other_null_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        assertContractsCount(1);

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", new CreateMessageContractsDto(List.of(contract2)));

        assertContractsCount(1);
    }


    @Test
    @SneakyThrows
    void put_invalid_contract() {
        NewMessageContractDto contract = new NewMessageContractDto("", "",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        List<NewMessageContractDto> uploadedContracts = List.of(contract);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);

        mockMvc.perform(put("/api/contracts/app/2.0.0")
                        .header("Authorization", basicAuth("write", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isBadRequest()); // 400
    }

    @Test
    @SneakyThrows
    void delete_contract_unauthorized() {
        mockMvc.perform(delete("/api/contracts/app/1"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void put_then_delete_contract() {
        MessageContractDto contract1 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "test-topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "2.0.0",
                "test-topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        // given: two uploaded contracts
        putContracts("app2", "3.0.0", messageContractsDto);

        // when: deleting contract 2
        mockMvc.perform(delete("/api/contracts/app2/3.0.0")
                        .param("messageType", "ActivZoneEnteredEvent")
                        .param("messageTypeVersion", "2.0.0")
                        .param("topic", "test-topic")
                        .param("role", "PRODUCER")
                        .header("Authorization", basicAuth("write", "secret")))
                .andExpect(status().is2xxSuccessful());

        // then: expect only contract 1 to exist
        MvcResult result = mockMvc.perform(get("/api/contracts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        List<MessageContractDto> dtos = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(dtos.contains(contract1));
        assertFalse(dtos.contains(contract2));
    }

    @Test
    @SneakyThrows
    void delete_contract_uploadRole_forbidden() {
        mockMvc.perform(delete("/api/contracts/app2/3.0.0")
                        .param("messageType", "ActivZoneEnteredEvent")
                        .param("messageTypeVersion", "2.0.0")
                        .param("topic", "test-topic")
                        .param("role", "PRODUCER")
                        .header("Authorization", basicAuth("upload", "secret")))
                .andExpect(status().isForbidden()); // 403
    }

    @Test
    @SneakyThrows
    void put_then_get_by_environment() {
        MessageContractDto contract1 = new MessageContractDto("myAppName", "version1", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("myAppName", "version1", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        MessageContractDto contract3 = new MessageContractDto("myAppName", "version2", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");

        putContracts("myAppName", "version1", new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2))));
        putContracts("myAppName", "version2", new CreateMessageContractsDto(singletonList(createNew(contract3))));

        // put deployment v1 - PROD
        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "version1", "prod")
                        .header("Authorization", basicAuth("write", "secret")))
                .andExpect(status().isCreated()); // 201


        // GET /api/contracts?env=prod
        MvcResult prodResult = mockMvc.perform(get("/api/contracts")
                        .param("env", "prod")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> prodDtos = objectMapper.readValue(
                prodResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(prodDtos.contains(contract1));
        assertTrue(prodDtos.contains(contract2));
        assertFalse(prodDtos.contains(contract3));

        // GET /api/contracts?env=ref (should be empty)
        MvcResult refResult = mockMvc.perform(get("/api/contracts")
                        .param("env", "ref")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> refDtos = objectMapper.readValue(
                refResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
        );

        assertTrue(refDtos.isEmpty());

        // PUT /api/deployments/{appName}/{appVersion}/{environment} for version2/ref
        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "version2", "ref")
                        .header("Authorization", basicAuth("write", "secret")))
                .andExpect(status().isCreated()); // 201

        // GET /api/contracts?env=ref (should now contain contract3)
        MvcResult refResultAfter = mockMvc.perform(get("/api/contracts")
                        .param("env", "ref")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        List<MessageContractDto> refDtosAfter = objectMapper.readValue(
                refResultAfter.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MessageContractDto.class)
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
        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                        .param("transactionId", transactionId)
                        .header("Authorization", basicAuth("write", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    private void putContracts(String appName, String appVersion, CreateMessageContractsDto messageContractsDto) {
        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                        .header("Authorization", basicAuth("write", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    private void putLegacyContracts(String appName, String appVersion, LegacyCreateMessageContractsDto legacyMessageContractsDto) {
        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                        .header("Authorization", basicAuth("write", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legacyMessageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }

}
