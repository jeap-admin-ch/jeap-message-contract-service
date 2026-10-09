package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.PersistenceConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class})
class DeploymentServiceTest {

    private final DeploymentService deploymentService;
    private final MessageContractRepository messageContractRepository;

    @MockitoBean
    private MessageTypeRepositoryFactory messageTypeRepositoryFactory;

    @Autowired
    DeploymentServiceTest(DeploymentService deploymentService,
                          MessageContractRepository messageContractRepository) {
        this.deploymentService = deploymentService;
        this.messageContractRepository = messageContractRepository;
    }

    @Test
    void saveNewDeploymentAppNameIsNotDefinedDeploymentNotSaved() {
        deploymentService.saveNewDeployment("unknown", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).isEmpty();
    }


    @Test
    void saveNewDeploymentAppNameIsDefinedDeploymentSaved() {
        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(appV1Contracts);

        deploymentService.saveNewDeployment("app", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(1);
    }

    @Test
    void saveNewDeploymentNoMessageContractsDeclaredDeploymentSaved() {
        messageContractRepository.saveContracts(List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        ));

        Deployment deployment = deploymentService.saveNewDeployment("app", "v2", "ABN", true);

        assertThat(deployment).isNotNull();
        assertThat(deployment.getAppVersion()).isEqualTo("v2");
    }

    @Test
    void saveNewDeploymentNoMessageContractsDeclaredAppWithOnlyDeletedContractsDeploymentSaved() {
        messageContractRepository.saveContracts(List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        ));
        messageContractRepository.deleteContract("app", "v1", "TestType1", "1.0.0", "topic", MessageContractRole.CONSUMER);

        Deployment deployment = deploymentService.saveNewDeployment("app", "v2", "ABN", true);

        assertThat(deployment).isNotNull();
    }

    @Test
    void saveNewDeploymentNoMessageContractsDeclaredAppNameUnknownDeploymentIgnored() {
        messageContractRepository.saveContracts(List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        ));

        Deployment deployment = deploymentService.saveNewDeployment("app-typo", "v1", "ABN", true);

        assertThat(deployment).isNull();
        assertThat(deploymentService.findLast10Deployments()).isEmpty();
    }

    @Test
    void saveNewDeploymentNoMessageContractsDeclaredButContractsExistDeploymentSavedWithItsContracts() {
        messageContractRepository.saveContracts(List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        ));

        Deployment deployment = deploymentService.saveNewDeployment("app", "v1", "ABN", true);

        assertThat(deployment).isNotNull();
        assertThat(deployment.getAppVersion()).isEqualTo("v1");
    }

    @Test
    void deleteDeploymentIsDeleted() {
        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(appV1Contracts);

        deploymentService.saveNewDeployment("app", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(1);

        deploymentService.deleteDeployment("app", "ABN");
        assertThat(deploymentService.findLast10Deployments()).isEmpty();
    }

    @Test
    void deleteDeploymentOnlyAppIsDeleted() {
        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(appV1Contracts);
        List<MessageContract> app1V1Contracts = List.of(
                MessageContractTestFactory.createContract("app1", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(app1V1Contracts);

        deploymentService.saveNewDeployment("app", "v1", "ABN");
        deploymentService.saveNewDeployment("app1", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(2);

        deploymentService.deleteDeployment("app", "ABN");
        List<Deployment> last10Deployments = deploymentService.findLast10Deployments();
        assertThat(last10Deployments).hasSize(1);
        assertThat(last10Deployments.get(0).getAppName()).isEqualTo("app1");
    }

}
