package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.DeploymentService;
import ch.admin.bit.jeap.messagecontract.domain.DomainConfiguration;
import ch.admin.bit.jeap.messagecontract.domain.MessageContractService;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class,
        MessageTypeRepositoryConfiguration.class})
class RenovateCompatibilityServiceTest {

    private static final String MESSAGE_TYPE = "ActivZoneEnteredEvent";
    private static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.activ:activ-zone-entered-event";
    private static final String CONSUMER = "consumer";
    private static final String PRODUCER = "producer";
    private static final String TOPIC = "test-topic";
    private static TestRegistryRepo testRegistry;

    private final RenovateCompatibilityService renovateService;
    private final MessageContractService contractService;
    private final DeploymentService deploymentService;

    @Autowired
    RenovateCompatibilityServiceTest(RenovateCompatibilityService renovateService,
                                     MessageContractService contractService,
                                     DeploymentService deploymentService) {
        this.renovateService = renovateService;
        this.contractService = contractService;
        this.deploymentService = deploymentService;
    }

    @BeforeAll
    static void createRegistry() throws Exception {
        testRegistry = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterAll
    static void deleteRegistry() throws Exception {
        testRegistry.delete();
    }

    @Test
    void globalCheckReturnsOnlyCandidateCompatibleWithDeployedProducer() {
        saveAndDeploy(PRODUCER, MessageContractRole.PRODUCER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findGloballyCompatibleReleases(
                PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).extracting(RenovateRelease::version).containsExactly("1.0.0");
    }

    @Test
    void globalCheckReturnsOnlyCandidateCompatibleWithDeployedConsumer() {
        saveAndDeploy(CONSUMER, MessageContractRole.CONSUMER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findGloballyCompatibleReleases(
                PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).extracting(RenovateRelease::version).containsExactly("1.0.0");
    }

    @Test
    void appSpecificCheckUsesRequestingConsumerRoleAndTopic() {
        saveAndDeploy(CONSUMER, MessageContractRole.CONSUMER, "1.0.0", TOPIC);
        saveAndDeploy(PRODUCER, MessageContractRole.PRODUCER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findCompatibleReleasesForApp(
                CONSUMER, PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).extracting(RenovateRelease::version).containsExactly("1.0.0");
    }

    @Test
    void appSpecificCheckUsesRequestingProducerRoleAndTopic() {
        saveAndDeploy(PRODUCER, MessageContractRole.PRODUCER, "1.0.0", TOPIC);
        saveAndDeploy(CONSUMER, MessageContractRole.CONSUMER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findCompatibleReleasesForApp(
                PRODUCER, PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).extracting(RenovateRelease::version).containsExactly("1.0.0");
    }

    @Test
    void appSpecificCheckFailsClosedWithoutDeployedCounterpart() {
        saveAndDeploy(CONSUMER, MessageContractRole.CONSUMER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findCompatibleReleasesForApp(
                CONSUMER, PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).isEmpty();
    }

    @Test
    void appSpecificCheckFailsClosedForUnknownApp() {
        saveAndDeploy(PRODUCER, MessageContractRole.PRODUCER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findCompatibleReleasesForApp(
                "unknown", PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).isEmpty();
    }

    @Test
    void globalCheckFailsClosedWithoutProdContracts() {
        List<RenovateRelease> releases = renovateService.findGloballyCompatibleReleases(
                PACKAGE_NAME, "0.9.0", "PROD");

        assertThat(releases).isEmpty();
    }

    @Test
    void globalCheckFailsClosedForWrongDefiningSystemInMavenGroup() {
        saveAndDeploy(PRODUCER, MessageContractRole.PRODUCER, "1.0.0", TOPIC);

        List<RenovateRelease> releases = renovateService.findGloballyCompatibleReleases(
                "ch.admin.bit.jeap.messagetype.wrong:activ-zone-entered-event", "0.9.0", "PROD");

        assertThat(releases).isEmpty();
    }

    private void saveContract(String appName, String appVersion, MessageContractRole role, String messageTypeVersion) {
        saveContract(appName, appVersion, role, messageTypeVersion, TOPIC);
    }

    private void saveContract(String appName, String appVersion, MessageContractRole role, String messageTypeVersion,
                               String topic) {
        MessageContract contract = MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(MESSAGE_TYPE)
                .messageTypeVersion(messageTypeVersion)
                .topic(topic)
                .role(role)
                .registryUrl(testRegistry.url())
                .commitHash(testRegistry.revision())
                .branch("master")
                .compatibilityMode(CompatibilityMode.NONE)
                .build();
        contractService.saveContracts(appName, appVersion, null, List.of(contract));
    }

    private void saveAndDeploy(String appName, MessageContractRole role, String messageTypeVersion, String topic) {
        saveContract(appName, "1.0.0", role, messageTypeVersion, topic);
        deploymentService.saveNewDeployment(appName, "1.0.0", "PROD");
    }
}
