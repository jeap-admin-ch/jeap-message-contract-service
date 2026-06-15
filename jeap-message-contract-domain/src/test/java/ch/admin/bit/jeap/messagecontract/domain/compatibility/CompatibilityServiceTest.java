package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.domain.DeploymentService;
import ch.admin.bit.jeap.messagecontract.domain.DomainConfiguration;
import ch.admin.bit.jeap.messagecontract.domain.MessageContractService;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.ConsumerProducerInteraction;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.InteractionRole;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.PersistenceConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole.CONSUMER;
import static ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole.PRODUCER;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class, MessageTypeRepositoryConfiguration.class})
class CompatibilityServiceTest {

    private static final String TEST_CONSUMER = "test-consumer";
    private static final String TEST_PRODUCER = "test-producer";
    private static final String TEST_PRODUCER_2 = "test-producer-2";
    private static final String VERSION_1_0_0 = "1.0.0";
    private static final String VERSION_2_0_0 = "2.0.0";
    private static final String ACTIV_ZONE_ENTERED_EVENT = "ActivZoneEnteredEvent";
    private static final String TOPIC = "topic";
    private static final String TRANSACTION_ID = "transactionId";

    private final CompatibilityService compatibilityService;
    private final MessageContractService messageContractService;
    private final DeploymentService deploymentService;

    @Autowired
    CompatibilityServiceTest(CompatibilityService compatibilityService,
                              MessageContractService messageContractService,
                              DeploymentService deploymentService) {
        this.compatibilityService = compatibilityService;
        this.messageContractService = messageContractService;
        this.deploymentService = deploymentService;
    }

    private static TestRegistryRepo testRepo;

    @BeforeAll
    static void prepareRepository() throws Exception {
        testRepo = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterAll
    static void deleteRepository() throws Exception {
        testRepo.delete();
    }

    @Test
    void canIDeployAllConsumerInteractionsCompatibleShouldBeCompatible() {
        // given: one consumer, two producers of the same type on the same topic, deployed on prod
        saveActivZoneEnteredEventContract(TEST_CONSUMER, "1.0", CONSUMER, VERSION_1_0_0);
        saveActivZoneEnteredEventContract(TEST_PRODUCER, "1.0", PRODUCER, VERSION_1_0_0);
        saveActivZoneEnteredEventContract(TEST_PRODUCER_2, "2.0", PRODUCER, VERSION_1_0_0);
        deploymentService.saveNewDeployment(TEST_PRODUCER, "1.0", "prod");
        deploymentService.saveNewDeployment(TEST_PRODUCER_2, "2.0", "prod");

        // when: checking compatibility of the consumer with the producers
        CompatibilityCheckResult result = compatibilityService.checkCompatibility(TEST_CONSUMER, "1.0", "prod");

        // then: expect both producer interactions to be compatible
        assertThat(result.compatible()).isTrue();
        assertThat(result.interactions())
                .containsOnlyOnce(
                        compatibleInteraction(TEST_PRODUCER, "1.0",
                                ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0, TOPIC, InteractionRole.PRODUCER),
                        compatibleInteraction(TEST_PRODUCER_2, "2.0",
                                ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0, TOPIC, InteractionRole.PRODUCER));
        assertThat(result.incompatibilities()).isEmpty();
    }

    @Test
    void canIDeployNoInteractionsShouldBeCompatible() {
        // given: no contracts or deployments

        // when
        CompatibilityCheckResult result = compatibilityService.checkCompatibility("my-app", "1.0", "prod");

        // then
        assertThat(result.compatible()).isTrue();
        assertThat(result.interactions()).isEmpty();
        assertThat(result.incompatibilities()).isEmpty();
        assertThat(result.getMessage())
                .contains("No incompatible interactions found");
    }

    @Test
    void canIDeployIncompatibleSchemaShouldBeIncompatible() {
        // given: consumer consumer v1, producer produces incompatible event v2
        saveActivZoneEnteredEventContract(TEST_CONSUMER, "1.0", CONSUMER, VERSION_1_0_0);
        saveActivZoneEnteredEventContract(TEST_PRODUCER, "2.0", PRODUCER, VERSION_2_0_0);
        deploymentService.saveNewDeployment(TEST_PRODUCER, "2.0", "prod");
        ConsumerProducerInteraction interaction = incompatibleInteraction(TEST_PRODUCER, "2.0",
                ACTIV_ZONE_ENTERED_EVENT, VERSION_2_0_0, TOPIC, InteractionRole.PRODUCER);

        // when
        CompatibilityCheckResult result = compatibilityService.checkCompatibility(TEST_CONSUMER, "1.0", "prod");

        // then
        assertThat(result.compatible()).isFalse();
        assertThat(result.interactions()).containsOnlyOnce(interaction);
        assertThat(result.incompatibilities()).hasSize(1);
        assertThat(result.incompatibilities().get(0).target())
                .isEqualTo(interaction);
        assertThat(result.incompatibilities().get(0).schemaIncompatibilities())
                .isNotEmpty();
        assertThat(result.getMessage())
                .contains("App test-consumer:1.0 is consuming message type ActivZoneEnteredEvent:1.0.0")
                .contains("App test-producer:2.0 is producing message type ActivZoneEnteredEvent:2.0.0")
                .contains("NAME_MISMATCH");
    }

    @Test
    void canIDeployIncompatibleSchemaButDifferentTopicShouldBeCompatible() {
        // given: consumer consumes v1 on topic, producer produces incompatible event v2 on topic-2
        saveActivZoneEnteredEventContract(TEST_CONSUMER, "1.0", CONSUMER, VERSION_1_0_0);
        messageContractService.saveContracts(TEST_PRODUCER, "2.0", TRANSACTION_ID, List.of(
                createActivZoneEnteredEventContract(TEST_PRODUCER, "2.0", VERSION_2_0_0, PRODUCER, "topic-2")));
        deploymentService.saveNewDeployment(TEST_PRODUCER, "2.0", "prod");

        // when: deploying the consumer, it should be compatible os the consumer/producer have contracts for different topics
        CompatibilityCheckResult result = compatibilityService.checkCompatibility(TEST_CONSUMER, "1.0", "prod");

        // then
        assertThat(result.compatible()).isTrue();
        assertThat(result.interactions()).isEmpty();
        assertThat(result.incompatibilities()).isEmpty();
    }

    @Test
    void canIDeployInteractedComponentsNotDeployedOnEnvironmentShouldBeCompatible() {
        // given: an incompatible interaction between consumer and producer, with the producer on ref
        saveActivZoneEnteredEventContract(TEST_CONSUMER, "1.0", CONSUMER, VERSION_1_0_0);
        saveActivZoneEnteredEventContract(TEST_PRODUCER, "1.0", PRODUCER, VERSION_2_0_0);
        deploymentService.saveNewDeployment(TEST_PRODUCER, "1.0", "ref");

        // when: deploying the consumer to prod, the incompatible deployment on ref should not block it
        CompatibilityCheckResult result = compatibilityService.checkCompatibility(TEST_CONSUMER, "1.0", "prod");

        // then
        assertThat(result.compatible()).isTrue();
        assertThat(result.interactions()).isEmpty();
        assertThat(result.incompatibilities()).isEmpty();
    }

    @Test
    void canIDeployUnknownAppOrAppVersionShouldBeAbleToDeploy() {
        // given: a deployment with a contract for a consumer
        saveActivZoneEnteredEventContract(TEST_CONSUMER, "1.0", CONSUMER, VERSION_1_0_0);
        deploymentService.saveNewDeployment(TEST_CONSUMER, "1.0", "ref");

        // when: deploying another, unknown app, this should be possible
        CompatibilityCheckResult result = compatibilityService.checkCompatibility(TEST_CONSUMER, "1.0", "prod");

        // then
        assertThat(result.compatible()).isTrue();
        assertThat(result.interactions()).isEmpty();
        assertThat(result.incompatibilities()).isEmpty();
    }

    private static ConsumerProducerInteraction compatibleInteraction(
            String appName, String appVersion, String messageType, String messageTypeVersion, String topic, InteractionRole role) {
        return new ConsumerProducerInteraction(appName, appVersion, messageType, messageTypeVersion, topic, role);
    }

    private static ConsumerProducerInteraction incompatibleInteraction(
            String appName, String appVersion, String messageType, String messageTypeVersion, String topic, InteractionRole role) {
        return new ConsumerProducerInteraction(appName, appVersion, messageType, messageTypeVersion, topic, role);
    }

    private MessageContract createActivZoneEnteredEventContract(
            String appName, String appVersion, String messageTypeVersion, MessageContractRole role, String topic) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(ACTIV_ZONE_ENTERED_EVENT)
                .messageTypeVersion(messageTypeVersion)
                .topic(topic)
                .role(role)
                .registryUrl(testRepo.url())
                .commitHash(testRepo.revision())
                .branch("master")
                .compatibilityMode(CompatibilityMode.NONE)
                .build();
    }

    private void saveActivZoneEnteredEventContract(String appName, String appVersion, MessageContractRole consumer, String messageTypeVersion) {
        messageContractService.saveContracts(appName, appVersion, TRANSACTION_ID, List.of(
                createActivZoneEnteredEventContract(appName, appVersion, messageTypeVersion, consumer, TOPIC)));
    }
}
