package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.ConsumerProducerInteraction;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.Incompatibility;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.InteractionRole;
import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import ch.admin.bit.jeap.messagecontract.web.api.dto.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.web.api.dto.CreateMessageContractsDto;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.web.api.dto.NewMessageContractDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("ConstantConditions")
class DeploymentControllerCompatibilityTest extends ControllerTestBase {

    private static final String AUTHORIZATION = "Authorization";
    private static final String WRITE_SECRET = "write:secret";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String API_CONTRACTS_APP_VERSION = "/api/contracts/{appName}/{appVersion}";
    private static final String API_DEPLOYMENTS_APP_ENV = "/api/deployments/{appName}/{appVersion}/{environment}";
    private static final String TEST_TOPIC = "test-topic";
    private static final String VERSION_2_0_0 = "2.0.0";
    private static final String ACTIV_ZONE_ENTERED_EVENT = "ActivZoneEnteredEvent";
    private static final String TEST_CONSUMER_APP = "test-consumer-app";
    private static final String TEST_PRODUCER_APP = "test-producer-app";

    private final JpaDeploymentRepository deploymentRepository;
    private final JpaMessageContractRepository messageContractRepository;
    private final JsonMapper jsonMapper;
    private TestRegistryRepo testRegistryRepo;

    @Autowired
    DeploymentControllerCompatibilityTest(MockMvc mockMvc,
                                           JpaDeploymentRepository deploymentRepository,
                                           JpaMessageContractRepository messageContractRepository,
                                           JsonMapper jsonMapper) {
        super(mockMvc);
        this.deploymentRepository = deploymentRepository;
        this.messageContractRepository = messageContractRepository;
        this.jsonMapper = jsonMapper;
    }

    @BeforeEach
    void createTestMessageRegistry() throws Exception {
        testRegistryRepo = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterEach
    void clearDatabase() throws Exception {
        testRegistryRepo.delete();
        deploymentRepository.deleteAll();
        messageContractRepository.deleteAll();
    }

    @Test
    @SneakyThrows
    void getCompatibilityUnknownAppShouldReturnOk() {
        // given: one known app
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "ref");

        // when an unknown app is tested for compatibility, then expect it to be compatible with the environment as it
        // has no contracts
        doCompatibilityGetRequest("unknown-app", "1.0", "ref")
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    void getCompatibilityUnauthorizedShouldReturn40x() {
        // 1️⃣ No auth header
        mockMvc.perform(get("/api/deployments/compatibility/unknown-app/1.0/ref"))
                .andExpect(status().isUnauthorized()); // 401

        // 2️⃣ Invalid credentials
        String invalidAuthHeader = BASIC_PREFIX + Base64.getEncoder()
                .encodeToString(("write:bad-secret").getBytes());

        mockMvc.perform(get("/api/deployments/compatibility/unknown-app/1.0/ref")
                        .header(AUTHORIZATION, invalidAuthHeader))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void getCompatibilityWhenSingleConsumerContractIsCompatibleThenShouldReturnOk() {
        // given: a single consumer/producer target with compatible schemas
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.PRODUCER, TEST_PRODUCER_APP, "2.0");

        // given: consumer/producer apps are deployed on prod
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "prod");
        notifyAppDeployedOnEnv(TEST_PRODUCER_APP, "2.0", "prod");

        // when testing compatibility, then expect response to be OK
        MvcResult mvcConsumerResult = doCompatibilityGetRequest(TEST_CONSUMER_APP, "1.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        CompatibilityCheckResult consumerResult =  jsonMapper.readValue(mvcConsumerResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        MvcResult mvcProducerResult = doCompatibilityGetRequest(TEST_PRODUCER_APP, "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        CompatibilityCheckResult producerResult =  jsonMapper.readValue(mvcProducerResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected compatible interactions
        assertThat(consumerResult.compatible()).isTrue();
        assertThat(consumerResult.interactions()).hasSize(1);
        assertThat(consumerResult.incompatibilities()).isEmpty();
        ConsumerProducerInteraction consumeInteraction = consumerResult.interactions().getFirst();
        assertThat(consumeInteraction.topic()).isEqualTo(TEST_TOPIC);
        assertThat(consumeInteraction.appName()).isEqualTo(TEST_PRODUCER_APP);
        assertThat(consumeInteraction.appVersion()).isEqualTo("2.0");
        assertThat(consumeInteraction.messageType()).isEqualTo(ACTIV_ZONE_ENTERED_EVENT);
        assertThat(consumeInteraction.messageTypeVersion()).isEqualTo(VERSION_2_0_0);
        assertThat(consumeInteraction.role()).isEqualTo(InteractionRole.PRODUCER);

        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).hasSize(1);
        assertThat(producerResult.incompatibilities()).isEmpty();
        ConsumerProducerInteraction producerInteraction = producerResult.interactions().getFirst();
        assertThat(producerInteraction.topic()).isEqualTo(TEST_TOPIC);
        assertThat(producerInteraction.appName()).isEqualTo(TEST_CONSUMER_APP);
        assertThat(producerInteraction.appVersion()).isEqualTo("1.0");
        assertThat(producerInteraction.messageType()).isEqualTo(ACTIV_ZONE_ENTERED_EVENT);
        assertThat(producerInteraction.messageTypeVersion()).isEqualTo(VERSION_2_0_0);
        assertThat(producerInteraction.role()).isEqualTo(InteractionRole.CONSUMER);
    }

    @Test
    @SneakyThrows
    void getCompatibilityWhenTwoProducerContractsAreCompatibleThenShouldReturnOk() {
        // given: two consumer/producer interactions with compatible schemas
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, "test-consumer-app-2", "1.0");
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.PRODUCER, TEST_PRODUCER_APP, "2.0");

        // given: both consumer apps are deployed on prod
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "prod");
        notifyAppDeployedOnEnv("test-consumer-app-2", "1.0", "prod");

        // when testing compatibility before deploying the producer, the producer should be compatible
        MvcResult mvcResult = doCompatibilityGetRequest(TEST_PRODUCER_APP, "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  jsonMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected compatible interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).hasSize(2);
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @Test
    @SneakyThrows
    void getCompatibilityWhenConsumerContractIsIncompatibleThenShouldReturnPreconditionFailed() {
        // given: a consumer consuming v2, which is incompatible with v1 of the message the producer is using
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");
        putActivZoneEnteredEventContract("1.0.0", MessageContractRole.PRODUCER, TEST_PRODUCER_APP, "2.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "prod");

        // when testing producer compatibility, then expect response to be Precondition Failed
        MvcResult mvcResult = doCompatibilityGetRequest(TEST_PRODUCER_APP, "2.0", "prod")
                .andExpect(status().is(HttpStatus.PRECONDITION_FAILED.value()))
                .andReturn();

        CompatibilityCheckResult producerResult =  jsonMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected interactions and incompatibilities
        assertThat(producerResult.compatible()).isFalse();
        assertThat(producerResult.interactions()).hasSize(1);
        ConsumerProducerInteraction producerInteraction = producerResult.interactions().getFirst();
        assertThat(producerInteraction.topic()).isEqualTo(TEST_TOPIC);
        assertThat(producerInteraction.appName()).isEqualTo(TEST_CONSUMER_APP);
        assertThat(producerInteraction.appVersion()).isEqualTo("1.0");
        assertThat(producerInteraction.messageType()).isEqualTo(ACTIV_ZONE_ENTERED_EVENT);
        assertThat(producerInteraction.messageTypeVersion()).isEqualTo(VERSION_2_0_0);
        assertThat(producerInteraction.role()).isEqualTo(InteractionRole.CONSUMER);
        assertThat(producerResult.incompatibilities()).hasSizeGreaterThan(0);
        assertThat(producerResult.incompatibilities()).allMatch(incompatibility ->
                incompatibility.target().equals(producerInteraction));
        assertThat(producerResult.incompatibilities())
                .flatExtracting(Incompatibility::schemaIncompatibilities)
                .anyMatch(schemaIncompatibility -> schemaIncompatibility.incompatibilityType().equals("NAME_MISMATCH") &&
                        schemaIncompatibility.message().equals("expected: ch.admin.ezv.activ.infrastructure.kafka.event.ZoneReferences"));
    }

    @Test
    @SneakyThrows
    void getCompatibilityWhenNoContractForProducerThenShouldReturnOk() {
        // given: a consumer consuming v2, and no contract for the producer
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "prod");

        // when testing producer compatibility, then expect response to be Precondition Failed
        MvcResult mvcResult = doCompatibilityGetRequest(TEST_PRODUCER_APP, "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  jsonMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to signal compatibility without any found interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).isEmpty();
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @Test
    @SneakyThrows
    void getCompatibilityWhenNoInteractionOnEnvironmentContractForProducerThenShouldReturnOk() {
        // given: a consumer consuming v2, which is incompatible with v1 of the message the producer is using
        putActivZoneEnteredEventContract(VERSION_2_0_0, MessageContractRole.CONSUMER, TEST_CONSUMER_APP, "1.0");
        putActivZoneEnteredEventContract("1.0.0", MessageContractRole.PRODUCER, TEST_PRODUCER_APP, "2.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv(TEST_CONSUMER_APP, "1.0", "prod");

        // when testing producer compatibility on REF, then expect response to be OK
        MvcResult mvcResult = doCompatibilityGetRequest(TEST_PRODUCER_APP, "2.0", "ref")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  jsonMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to signal compatibility without any found interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).isEmpty();
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @SneakyThrows
    protected void notifyAppDeployedOnEnv(String appName, String appVersion, String environment) {
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder().encodeToString((WRITE_SECRET).getBytes());

        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV, appName, appVersion, environment)
                        .header(AUTHORIZATION, basicAuthHeader))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    protected ResultActions doCompatibilityGetRequest(String appName, String appVersion, String environment) {
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder().encodeToString((WRITE_SECRET).getBytes());

        return mockMvc.perform(get("/api/deployments/compatibility/{appName}/{appVersion}/{environment}", appName, appVersion, environment)
                        .header(AUTHORIZATION, basicAuthHeader));
    }

    private void putActivZoneEnteredEventContract(String messageTypeVersion, MessageContractRole consumer, String appName, String appVersion) {
        NewMessageContractDto consumerContract =
                new NewMessageContractDto(ACTIV_ZONE_ENTERED_EVENT, messageTypeVersion,
                        TEST_TOPIC, consumer,
                        testRegistryRepo.url(), testRegistryRepo.revision(), "master", CompatibilityMode.BACKWARD, null);
        CreateMessageContractsDto messageContractsDto1 = new CreateMessageContractsDto(List.of(consumerContract));
        putContracts(appName, appVersion, messageContractsDto1);
    }

    @SneakyThrows
    protected void putContracts(String appName, String appVersion, CreateMessageContractsDto messageContractsDto) {
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder()
                .encodeToString((WRITE_SECRET).getBytes());

        mockMvc.perform(put(API_CONTRACTS_APP_VERSION, appName, appVersion)
                        .header(AUTHORIZATION, basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }
}
