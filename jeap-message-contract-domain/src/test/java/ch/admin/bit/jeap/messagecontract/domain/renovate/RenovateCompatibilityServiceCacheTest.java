package ch.admin.bit.jeap.messagecontract.domain.renovate;

import ch.admin.bit.jeap.messagecontract.domain.DeploymentService;
import ch.admin.bit.jeap.messagecontract.domain.DomainConfiguration;
import ch.admin.bit.jeap.messagecontract.domain.MessageContractService;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies with the default configuration that a burst of Renovate requests for the same message type and environment
 * queries the deployed contracts only once.
 */
@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class,
        MessageTypeRepositoryConfiguration.class})
class RenovateCompatibilityServiceCacheTest {

    private static final String PACKAGE_NAME = "ch.admin.bit.jeap.messagetype.activ:activ-zone-entered-event";
    private static TestRegistryRepo testRegistry;

    @Autowired
    private RenovateCompatibilityService renovateService;
    @Autowired
    private MessageContractService contractService;
    @Autowired
    private DeploymentService deploymentService;
    @MockitoSpyBean
    private MessageContractRepository contractRepository;

    @BeforeAll
    static void createRegistry() throws Exception {
        testRegistry = TestRegistryRepo.createMessageTypeRegistryRepository();
    }

    @AfterAll
    static void deleteRegistry() throws Exception {
        testRegistry.delete();
    }

    @Test
    void repeatedRequestsWithinTtlAreServedFromCache() {
        saveAndDeploy("producer", MessageContractRole.PRODUCER);
        saveAndDeploy("consumer", MessageContractRole.CONSUMER);

        for (int i = 0; i < 5; i++) {
            assertThat(renovateService.findGloballyCompatibleReleases(PACKAGE_NAME, "0.9.0", "PROD"))
                    .extracting(RenovateRelease::version).containsExactly("1.0.0");
            assertThat(renovateService.findCompatibleReleasesForApp("consumer", PACKAGE_NAME, "0.9.0", "PROD"))
                    .extracting(RenovateRelease::version).containsExactly("1.0.0");
        }

        verify(contractRepository, times(1)).findCurrentlyDeployedContracts(anyString(), anyString());
    }

    private void saveAndDeploy(String appName, MessageContractRole role) {
        MessageContract contract = MessageContract.builder()
                .appName(appName)
                .appVersion("1.0.0")
                .messageType("ActivZoneEnteredEvent")
                .messageTypeVersion("1.0.0")
                .topic("test-topic")
                .role(role)
                .registryUrl(testRegistry.url())
                .commitHash(testRegistry.revision())
                .branch("master")
                .compatibilityMode(CompatibilityMode.NONE)
                .build();
        contractService.saveContracts(appName, "1.0.0", null, List.of(contract));
        deploymentService.saveNewDeployment(appName, "1.0.0", "PROD");
    }
}
