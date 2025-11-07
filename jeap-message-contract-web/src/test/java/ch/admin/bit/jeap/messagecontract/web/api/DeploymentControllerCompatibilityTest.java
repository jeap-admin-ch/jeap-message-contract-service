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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("ConstantConditions")
class DeploymentControllerCompatibilityTest extends ControllerTestBase {

    @Autowired
    private JpaDeploymentRepository deploymentRepository;
    @Autowired
    private JpaMessageContractRepository messageContractRepository;
    @Autowired
    private ObjectMapper objectMapper;
    private TestRegistryRepo testRegistryRepo;

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
    void get_compatibility_unknownApp_shouldReturnOk() {
        // given: one known app
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "ref");

        // when an unknown app is tested for compatibility, then expect it to be compatible with the environment as it
        // has no contracts
        doCompatibilityGetRequest("unknown-app", "1.0", "ref")
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    void get_compatibility_unauthorized_shouldReturn40x() {
        // 1️⃣ No auth header
        mockMvc.perform(get("/api/deployments/compatibility/unknown-app/1.0/ref"))
                .andExpect(status().isUnauthorized()); // 401

        // 2️⃣ Invalid credentials
        String invalidAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("write:bad-secret").getBytes());

        mockMvc.perform(get("/api/deployments/compatibility/unknown-app/1.0/ref")
                        .header("Authorization", invalidAuthHeader))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    @SneakyThrows
    void get_compatibility_whenSingleConsumerContractIsCompatible_thenShouldReturnOk() {
        // given: a single consumer/producer target with compatible schemas
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.PRODUCER, "test-producer-app", "2.0");

        // given: consumer/producer apps are deployed on prod
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "prod");
        notifyAppDeployedOnEnv("test-producer-app", "2.0", "prod");

        // when testing compatibility, then expect response to be OK
        MvcResult mvcConsumerResult = doCompatibilityGetRequest("test-consumer-app", "1.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        CompatibilityCheckResult consumerResult =  objectMapper.readValue(mvcConsumerResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        MvcResult mvcProducerResult = doCompatibilityGetRequest("test-producer-app", "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        CompatibilityCheckResult producerResult =  objectMapper.readValue(mvcProducerResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected compatible interactions
        assertThat(consumerResult.compatible()).isTrue();
        assertThat(consumerResult.interactions()).hasSize(1);
        assertThat(consumerResult.incompatibilities()).isEmpty();
        ConsumerProducerInteraction consumeInteraction = consumerResult.interactions().getFirst();
        assertThat(consumeInteraction.topic()).isEqualTo("test-topic");
        assertThat(consumeInteraction.appName()).isEqualTo("test-producer-app");
        assertThat(consumeInteraction.appVersion()).isEqualTo("2.0");
        assertThat(consumeInteraction.messageType()).isEqualTo("ActivZoneEnteredEvent");
        assertThat(consumeInteraction.messageTypeVersion()).isEqualTo("2.0.0");
        assertThat(consumeInteraction.role()).isEqualTo(InteractionRole.PRODUCER);

        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).hasSize(1);
        assertThat(producerResult.incompatibilities()).isEmpty();
        ConsumerProducerInteraction producerInteraction = producerResult.interactions().getFirst();
        assertThat(producerInteraction.topic()).isEqualTo("test-topic");
        assertThat(producerInteraction.appName()).isEqualTo("test-consumer-app");
        assertThat(producerInteraction.appVersion()).isEqualTo("1.0");
        assertThat(producerInteraction.messageType()).isEqualTo("ActivZoneEnteredEvent");
        assertThat(producerInteraction.messageTypeVersion()).isEqualTo("2.0.0");
        assertThat(producerInteraction.role()).isEqualTo(InteractionRole.CONSUMER);
    }

    @Test
    @SneakyThrows
    void get_compatibility_whenTwoProducerContractsAreCompatible_thenShouldReturnOk() {
        // given: two consumer/producer interactions with compatible schemas
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app-2", "1.0");
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.PRODUCER, "test-producer-app", "2.0");

        // given: both consumer apps are deployed on prod
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "prod");
        notifyAppDeployedOnEnv("test-consumer-app-2", "1.0", "prod");

        // when testing compatibility before deploying the producer, the producer should be compatible
        MvcResult mvcResult = doCompatibilityGetRequest("test-producer-app", "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  objectMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected compatible interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).hasSize(2);
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @Test
    @SneakyThrows
    void get_compatibility_whenConsumerContractIsIncompatible_thenShouldReturnPreconditionFailed() {
        // given: a consumer consuming v2, which is incompatible with v1 of the message the producer is using
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");
        putActivZoneEnteredEventContract("1.0.0", MessageContractRole.PRODUCER, "test-producer-app", "2.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "prod");

        // when testing producer compatibility, then expect response to be Precondition Failed
        MvcResult mvcResult = doCompatibilityGetRequest("test-producer-app", "2.0", "prod")
                .andExpect(status().is(HttpStatus.PRECONDITION_FAILED.value()))
                .andReturn();

        CompatibilityCheckResult producerResult =  objectMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to contain the expected interactions and incompatibilities
        assertThat(producerResult.compatible()).isFalse();
        assertThat(producerResult.interactions()).hasSize(1);
        ConsumerProducerInteraction producerInteraction = producerResult.interactions().getFirst();
        assertThat(producerInteraction.topic()).isEqualTo("test-topic");
        assertThat(producerInteraction.appName()).isEqualTo("test-consumer-app");
        assertThat(producerInteraction.appVersion()).isEqualTo("1.0");
        assertThat(producerInteraction.messageType()).isEqualTo("ActivZoneEnteredEvent");
        assertThat(producerInteraction.messageTypeVersion()).isEqualTo("2.0.0");
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
    void get_compatibility_whenNoContractForProducer_thenShouldReturnOk() {
        // given: a consumer consuming v2, and no contract for the producer
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "prod");

        // when testing producer compatibility, then expect response to be Precondition Failed
        MvcResult mvcResult = doCompatibilityGetRequest("test-producer-app", "2.0", "prod")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  objectMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to signal compatibility without any found interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).isEmpty();
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @Test
    @SneakyThrows
    void get_compatibility_whenNoInteractionOnEnvironmentContractForProducer_thenShouldReturnOk() {
        // given: a consumer consuming v2, which is incompatible with v1 of the message the producer is using
        putActivZoneEnteredEventContract("2.0.0", MessageContractRole.CONSUMER, "test-consumer-app", "1.0");
        putActivZoneEnteredEventContract("1.0.0", MessageContractRole.PRODUCER, "test-producer-app", "2.0");

        // given: consumer app is deployed on prod
        notifyAppDeployedOnEnv("test-consumer-app", "1.0", "prod");

        // when testing producer compatibility on REF, then expect response to be OK
        MvcResult mvcResult = doCompatibilityGetRequest("test-producer-app", "2.0", "ref")
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        CompatibilityCheckResult producerResult =  objectMapper.readValue(mvcResult.getResponse().getContentAsString(), CompatibilityCheckResult.class);

        // then: expect response to signal compatibility without any found interactions
        assertThat(producerResult.compatible()).isTrue();
        assertThat(producerResult.interactions()).isEmpty();
        assertThat(producerResult.incompatibilities()).isEmpty();
    }

    @SneakyThrows
    protected void notifyAppDeployedOnEnv(String appName, String appVersion, String environment) {
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}", appName, appVersion, environment)
                        .header("Authorization", basicAuthHeader))
                .andExpect(status().isCreated()); // 201
    }

    @SneakyThrows
    protected ResultActions doCompatibilityGetRequest(String appName, String appVersion, String environment) {
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        return mockMvc.perform(get("/api/deployments/compatibility/{appName}/{appVersion}/{environment}", appName, appVersion, environment)
                        .header("Authorization", basicAuthHeader));
    }

    private void putActivZoneEnteredEventContract(String messageTypeVersion, MessageContractRole consumer, String appName, String appVersion) {
        NewMessageContractDto consumerContract =
                new NewMessageContractDto("ActivZoneEnteredEvent", messageTypeVersion,
                        "test-topic", consumer,
                        testRegistryRepo.url(), testRegistryRepo.revision(), "master", CompatibilityMode.BACKWARD, null);
        CreateMessageContractsDto messageContractsDto1 = new CreateMessageContractsDto(List.of(consumerContract));
        putContracts(appName, appVersion, messageContractsDto1);
    }

    @SneakyThrows
    protected void putContracts(String appName, String appVersion, CreateMessageContractsDto messageContractsDto) {
        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", appName, appVersion)
                        .header("Authorization", basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageContractsDto)))
                .andExpect(status().isCreated()); // 201
    }
}
