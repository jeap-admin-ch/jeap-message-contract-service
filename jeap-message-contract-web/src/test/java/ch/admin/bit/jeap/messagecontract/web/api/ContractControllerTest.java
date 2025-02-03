package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import ch.admin.bit.jeap.messagecontract.web.api.dto.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

class ContractControllerTest extends ControllerTestBase {

    private static TestRegistryRepo repo;
    private static String repoUrl;

    @Autowired
    private JpaMessageContractRepository messageContractRepository;

    @Autowired
    private JpaDeploymentRepository deploymentRepository;

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
    void get_contracts() {
        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Test
    void put_contracts_unauthorized() {
        webTestClient.put()
                .uri("/api/contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void putContract_uploadRole_success() {
        MessageContractDto contract1 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "test-topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "2.0.0",
                "test-topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        webTestClient.put()
                .uri("/api/contracts/{appName}/{appVersion}", "app", "1.0.0")
                .headers(headers -> headers.setBasicAuth("upload", "secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageContractsDto)
                .exchange()
                .expectStatus()
                .isEqualTo(201);
    }

    @Test
    void put_then_get_contracts() {
        MessageContractDto contract1 = new MessageContractDto("app", "2.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app", "2.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        putContracts("app", "2.0.0", messageContractsDto);

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(List.of(contract1, contract2), dtos));
    }

    @Test
    void put_then_get_contracts_with_legacy_create_contracts_dto() {
        MessageContractDto contract = new MessageContractDto("app", "1.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, null);

        LegacyCreateMessageContractsDto legacyCreateMessageContractsDto = new LegacyCreateMessageContractsDto(List.of(createLegacyNew(contract)));

        putLegacyContracts("app", "1.0.0", legacyCreateMessageContractsDto);

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(List.of(contract), dtos));
    }

    @Test
    void put_then_replace_contracts() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        List<NewMessageContractDto> uploadedContracts = List.of(contract1, contract2);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);
        putContracts("app", "2.0.0", messageContractsDto);

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(2, dtos.size()));

        List<NewMessageContractDto> replacedContracts = List.of(contract1);
        CreateMessageContractsDto replacedContractsDto = new CreateMessageContractsDto(replacedContracts);
        putContracts("app", "2.0.0", replacedContractsDto);

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));
    }

    @Test
    void put_then_add_contracts_for_same_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract2)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(2, dtos.size()));
    }

    @Test
    void put_then_add_same_contract_for_same_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));

        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));
    }

    @Test
    void put_then_add_contracts_for_other_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", "tx2", new CreateMessageContractsDto(List.of(contract2)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));
    }

    @Test
    void put_then_add_contracts_for_other_null_transaction() {
        NewMessageContractDto contract1 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        putContracts("app", "2.0.0", "tx1", new CreateMessageContractsDto(List.of(contract1)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));

        NewMessageContractDto contract2 = new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0",
                "topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        putContracts("app", "2.0.0", new CreateMessageContractsDto(List.of(contract2)));

        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertEquals(1, dtos.size()));
    }


    @Test
    void put_invalid_contract() {
        NewMessageContractDto contract = new NewMessageContractDto("", "",
                "topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        List<NewMessageContractDto> uploadedContracts = List.of(contract);
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(uploadedContracts);

        webTestClient.put()
                .uri("/api/contracts/app/2.0.0")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageContractsDto)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_contract_unauthorized() {
        webTestClient.delete()
                .uri("/api/contracts/app/1")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void put_then_delete_contract() {
        MessageContractDto contract1 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "1.0.0",
                "test-topic", MessageContractRole.CONSUMER, repoUrl, repo.revision(), null, CompatibilityMode.BACKWARD, null);
        MessageContractDto contract2 = new MessageContractDto("app2", "3.0.0", "ActivZoneEnteredEvent", "2.0.0",
                "test-topic", MessageContractRole.PRODUCER, repoUrl, null, "master", CompatibilityMode.FORWARD, "testKeyId");
        CreateMessageContractsDto messageContractsDto = new CreateMessageContractsDto(List.of(createNew(contract1), createNew(contract2)));

        // given: two uploaded contracts
        putContracts("app2", "3.0.0", messageContractsDto);

        // when: deleting contract 2
        webTestClient.delete()
                .uri("/api/contracts/app2/3.0.0?messageType=ActivZoneEnteredEvent&messageTypeVersion=2.0.0&topic=test-topic&role=PRODUCER")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .is2xxSuccessful();

        // then: expect only contract 1 to exist
        webTestClient.get()
                .uri("/api/contracts")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> {
                    assertTrue(dtos.contains(contract1));
                    assertFalse(dtos.contains(contract2));
                });
    }

    @Test
    void delete_contract_uploadRole_forbidden() {
        webTestClient.delete()
                .uri("/api/contracts/app2/3.0.0?messageType=ActivZoneEnteredEvent&messageTypeVersion=2.0.0&topic=test-topic&role=PRODUCER")
                .headers(headers -> headers.setBasicAuth("upload", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
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
        webTestClient.put()
                .uri("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "version1", "prod")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(201);


        webTestClient.get()
                .uri("/api/contracts?env=prod")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> {
                    assertTrue(dtos.contains(contract1));
                    assertTrue(dtos.contains(contract2));
                    assertFalse(dtos.contains(contract3));
                });

        webTestClient.get()
                .uri("/api/contracts?env=ref")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> assertTrue(dtos.isEmpty())               );

        // put deployment v2 - REF
        webTestClient.put()
                .uri("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "version2", "ref")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(201);

        webTestClient.get()
                .uri("/api/contracts?env=ref")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBodyList(MessageContractDto.class)
                .value(dtos -> {
                    assertFalse(dtos.contains(contract1));
                    assertFalse(dtos.contains(contract2));
                    assertTrue(dtos.contains(contract3));
                });
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

    private void putContracts(String appName, String appVersion, String transactionId, CreateMessageContractsDto messageContractsDto) {
        webTestClient.put()
                .uri("/api/contracts/{appName}/{appVersion}?transactionId={transactionId}", appName, appVersion, transactionId)
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageContractsDto)
                .exchange()
                .expectStatus()
                .isEqualTo(201);
    }


    private void putContracts(String appName, String appVersion, CreateMessageContractsDto messageContractsDto) {
        webTestClient.put()
                .uri("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageContractsDto)
                .exchange()
                .expectStatus()
                .isEqualTo(201);
    }

    private void putLegacyContracts(String appName, String appVersion, LegacyCreateMessageContractsDto legacyMessageContractsDto) {
        webTestClient.put()
                .uri("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(legacyMessageContractsDto)
                .exchange()
                .expectStatus()
                .isEqualTo(201);
    }

}
