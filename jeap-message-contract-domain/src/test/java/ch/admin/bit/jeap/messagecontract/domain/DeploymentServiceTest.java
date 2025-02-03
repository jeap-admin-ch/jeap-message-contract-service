package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.PersistenceConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.locations=classpath:db/migration/common")
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class})
class DeploymentServiceTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private MessageContractRepository messageContractRepository;

    @MockBean
    private MessageTypeRepositoryFactory messageTypeRepositoryFactory;

    @Test
    void saveNewDeployment_appNameIsNotDefined_deploymentNotSaved() {
        deploymentService.saveNewDeployment("unknown", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).isEmpty();
    }


    @Test
    void saveNewDeployment_appNameIsDefined_deploymentSaved() {
        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(appV1Contracts);

        deploymentService.saveNewDeployment("app", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(1);
    }

    @Test
    void deleteDeployment_isDeleted() {
        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null)
        );
        messageContractRepository.saveContracts(appV1Contracts);

        deploymentService.saveNewDeployment("app", "v1", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(1);

        deploymentService.deleteDeployment("app", "ABN");
        assertThat(deploymentService.findLast10Deployments()).hasSize(0);
    }

    @Test
    void deleteDeployment_onlyAppIsDeleted() {
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
