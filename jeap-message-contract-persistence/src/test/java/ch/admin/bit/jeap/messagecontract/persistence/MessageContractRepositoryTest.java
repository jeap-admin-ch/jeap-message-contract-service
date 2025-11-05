package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class MessageContractRepositoryTest {

    @Autowired
    private MessageContractRepository messageContractRepository;

    @Autowired
    private JpaMessageContractRepository jpaMessageContractRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Test
    void saveContracts() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, "testKey"));

        messageContractRepository.saveContracts(contracts);
        assertThat(jpaMessageContractRepository.findByDeletedFalse())
                .describedAs("should save contracts")
                .hasSize(2);

        messageContractRepository.saveContracts(contracts);
        assertThat(jpaMessageContractRepository.findByDeletedFalse())
                .describedAs("should not duplicate contracts")
                .hasSize(2);
    }

    @Test
    void getContractsForAppVersion() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null),
                createContract("app2", "v2", null, "master", MessageContractRole.PRODUCER, null));
        messageContractRepository.saveContracts(contracts);

        List<MessageContract> app2v1Contracts = messageContractRepository.getContractsForAppVersion("app2", "v1");

        assertThat(app2v1Contracts).hasSize(1);
    }

    @Test
    void distinctAppNameByRoleForMessageTypeOnTopic() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null),
                createContract("app2", "v2", null, "master", MessageContractRole.PRODUCER, null));
        messageContractRepository.saveContracts(contracts);

        Set<String> app1 = messageContractRepository.distinctAppNameByRoleForMessageTypeOnTopic(
                "TestType", "topic", MessageContractRole.CONSUMER);

        assertThat(app1).containsExactly("app1");
    }

    @Test
    void getContractsForAppVersionAndMessageTypeOnTopicWithRole() {
        MessageContract expectedContract = createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null);
        List<MessageContract> contracts = List.of(
                expectedContract,
                createContract("app1", "v1", "commitHash", null, MessageContractRole.PRODUCER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.CONSUMER, null));
        messageContractRepository.saveContracts(contracts);

        List<MessageContract> contract = messageContractRepository.getContractsForAppVersionAndMessageTypeOnTopicWithRole(
                "app1", "v1", "TestType", "topic", MessageContractRole.CONSUMER);

        assertThat(contract).containsExactly(expectedContract);
    }

    @Test
    void deleteContract() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null));

        messageContractRepository.saveContracts(contracts);
        messageContractRepository.deleteContract(
                "app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);

        assertThat(jpaMessageContractRepository.findByDeletedFalse())
                .hasSize(1);
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppName())
                .isEqualTo("app2");
    }

    @Test
    void deleteContractsForAppVersion() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app1", "v2", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null));
        messageContractRepository.saveContracts(contracts);

        messageContractRepository.deleteContractsForAppVersion("app1", "v1");

        assertThat(jpaMessageContractRepository.findByDeletedFalse()).hasSize(2);
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppName())
                .isEqualTo("app1");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppVersion())
                .isEqualTo("v2");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(1).getAppName())
                .isEqualTo("app2");
    }

    @Test
    void deleteContractsForAppVersionWithTransactionId() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app1", "v2", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null));
        messageContractRepository.saveContracts(contracts);

        messageContractRepository.deleteByAppNameAndAppVersionNotSameTransactionId("app1", "v1", "transactionId");

        assertThat(jpaMessageContractRepository.findByDeletedFalse()).hasSize(2);
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppName())
                .isEqualTo("app1");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppVersion())
                .isEqualTo("v2");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(1).getAppName())
                .isEqualTo("app2");
    }

    @Test
    void deleteContractsForAppVersionWithMultipleTransactionId() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "1", null, MessageContractRole.CONSUMER, null, "type1", "tx1"),
                createContract("app1", "v1", "1", null, MessageContractRole.PRODUCER, null, "type3", null),
                createContract("app1", "v1", "2", null, MessageContractRole.CONSUMER, null, "type2", "tx1"),
                createContract("app1", "v1", "3", null, MessageContractRole.PRODUCER, null, "type1", "tx2"));
        messageContractRepository.saveContracts(contracts);

        messageContractRepository.deleteByAppNameAndAppVersionNotSameTransactionId("app1", "v1", "tx2");

        assertThat(jpaMessageContractRepository.findByDeletedFalse()).hasSize(1);
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppName())
                .isEqualTo("app1");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getAppVersion())
                .isEqualTo("v1");
        assertThat(jpaMessageContractRepository.findByDeletedFalse().get(0).getCommitHash())
                .isEqualTo("3");
    }

    @Test
    void deleteContractsForAppVersionWithNewTransactionId() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "1", null, MessageContractRole.CONSUMER, null, "type1", "tx1"),
                createContract("app1", "v1", "1", null, MessageContractRole.PRODUCER, null, "type3", null),
                createContract("app1", "v1", "2", null, MessageContractRole.CONSUMER, null, "type2", "tx1"),
                createContract("app1", "v1", "3", null, MessageContractRole.PRODUCER, null, "type1", "tx2"));
        messageContractRepository.saveContracts(contracts);

        messageContractRepository.deleteByAppNameAndAppVersionNotSameTransactionId("app1", "v1", "tx3");

        assertThat(jpaMessageContractRepository.findByDeletedFalse()).isEmpty();
    }

    @Test
    void deleteContractsForAppVersionWithoutTransactionId() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "1", null, MessageContractRole.CONSUMER, null, "type1", "tx1"),
                createContract("app1", "v1", "1", null, MessageContractRole.PRODUCER, null, "type3", null),
                createContract("app1", "v1", "2", null, MessageContractRole.CONSUMER, null, "type2", "tx1"),
                createContract("app1", "v1", "3", null, MessageContractRole.PRODUCER, null, "type1", "tx2"));
        messageContractRepository.saveContracts(contracts);

        messageContractRepository.deleteContractsForAppVersion("app1", "v1");

        assertThat(jpaMessageContractRepository.findByDeletedFalse()).isEmpty();
    }

    @Test
    void existsByAppNameAndAppVersion() {
        List<MessageContract> contracts = List.of(
                createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app1", "v2", "commitHash", null, MessageContractRole.CONSUMER, null),
                createContract("app2", "v1", null, "master", MessageContractRole.PRODUCER, null));
        messageContractRepository.saveContracts(contracts);


        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app1", "v1")).isTrue();
        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app1", "v2")).isTrue();
        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app2", "v1")).isTrue();
        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app3", "v1")).isFalse();
    }

    @Test
    void findMessageContractInfosByEnvironment() {
        // given
        MessageContract app1v1 = createContract("app1", "v1", null, null, MessageContractRole.CONSUMER, null);
        MessageContract app2v1 = createContract("app2", "v1", null, null, MessageContractRole.PRODUCER, null);
        MessageContract app2v2 = createContract("app2", "v2", null, null, MessageContractRole.PRODUCER, null);
        List<MessageContract> contracts = List.of(app1v1, app2v1, app2v2);
        messageContractRepository.saveContracts(contracts);

        deploymentRepository.save(Deployment.builder().appName("app1").appVersion("v1").environment("prod").build());
        deploymentRepository.save(Deployment.builder().appName("app2").appVersion("v1").environment("prod").build());
        deploymentRepository.save(Deployment.builder().appName("app2").appVersion("v2").environment("ref").build());

        // when & then
        List<MessageContractInfo> prod = messageContractRepository.findMessageContractInfosByEnvironment("prod");
        assertThat(prod)
                .satisfiesExactly(
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app1")
                                .hasFieldOrPropertyWithValue("appVersion", "v1"),
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app2")
                                .hasFieldOrPropertyWithValue("appVersion", "v1"));

        List<MessageContractInfo> ref = messageContractRepository.findMessageContractInfosByEnvironment("ref");
        assertThat(ref)
                .satisfiesExactly(
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app2")
                                .hasFieldOrPropertyWithValue("appVersion", "v2"));

        assertThat(messageContractRepository.findMessageContractInfosByEnvironment("dev")).isEmpty();
    }

    @Test
    void findMessageContractInfosByEnvironment_onlyLatest() {
        // given
        MessageContract app1v1 = createContract("app1", "v1", null, null, MessageContractRole.CONSUMER, null);
        MessageContract app2v1 = createContract("app2", "v1", null, null, MessageContractRole.PRODUCER, null);
        MessageContract app2v2 = createContract("app2", "v2", null, null, MessageContractRole.PRODUCER, null);
        List<MessageContract> contracts = List.of(app1v1, app2v1, app2v2);
        messageContractRepository.saveContracts(contracts);

        deploymentRepository.save(Deployment.builder().appName("app1").appVersion("v1").environment("prod").build());
        deploymentRepository.save(Deployment.builder().appName("app2").appVersion("v1").environment("prod").build());
        deploymentRepository.save(Deployment.builder().appName("app2").appVersion("v2").environment("prod").build());

        // when & then
        List<MessageContractInfo> prod = messageContractRepository.findMessageContractInfosByEnvironment("prod");
        assertThat(prod)
                .satisfiesExactly(
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app1")
                                .hasFieldOrPropertyWithValue("appVersion", "v1"),
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app2")
                                .hasFieldOrPropertyWithValue("appVersion", "v2"));
    }

    @Test
    void findMessageContractInfosByEnvironment_omitDeleted() {
        // given
        MessageContract app1v1 = createContract("app1", "v1", null, null, MessageContractRole.CONSUMER, null);
        MessageContract app2v1 = createContract("app2", "v1", null, null, MessageContractRole.PRODUCER, null);
        List<MessageContract> contracts = List.of(app1v1, app2v1);
        messageContractRepository.saveContracts(contracts);

        deploymentRepository.save(Deployment.builder().appName("app1").appVersion("v1").environment("prod").build());
        deploymentRepository.save(Deployment.builder().appName("app2").appVersion("v1").environment("prod").build());

        // when & then
        messageContractRepository.deleteContract("app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);
        List<MessageContractInfo> prod = messageContractRepository.findMessageContractInfosByEnvironment("prod");
        assertThat(prod)
                .satisfiesExactly(
                        item -> assertThat(item)
                                .hasFieldOrPropertyWithValue("appName", "app2")
                                .hasFieldOrPropertyWithValue("appVersion", "v1"));
    }

    @Test
    void existsByAppNameAndAppVersion_contractDeleted_contractNotReturned() {
        //given
        List<MessageContract> contracts = List.of(createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null));
        messageContractRepository.saveContracts(contracts);
        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app1", "v1")).isTrue();

        //when
        messageContractRepository.deleteContract("app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);

        //then
        assertThat(messageContractRepository.existsByAppNameAndAppVersion("app1", "v1")).isFalse();
    }

    @Test
    void getContractsForAppVersion_contractDeleted_contractNotReturned() {
        //given
        List<MessageContract> contracts = List.of(createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null));
        messageContractRepository.saveContracts(contracts);
        assertThat(messageContractRepository.getContractsForAppVersion("app1", "v1")).hasSize(1);

        //when
        messageContractRepository.deleteContract("app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);

        //then
        assertThat(messageContractRepository.getContractsForAppVersion("app1", "v1")).isEmpty();
    }

    @Test
    void distinctAppNameByRoleForMessageTypeOnTopic_contractDeleted_contractNotReturned() {
        //given
        List<MessageContract> contracts = List.of(createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null));
        messageContractRepository.saveContracts(contracts);
        assertThat(messageContractRepository.distinctAppNameByRoleForMessageTypeOnTopic("TestType", "topic", MessageContractRole.CONSUMER)).hasSize(1);

        //when
        messageContractRepository.deleteContract("app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);

        //then
        assertThat(messageContractRepository.distinctAppNameByRoleForMessageTypeOnTopic("TestType", "topic", MessageContractRole.CONSUMER)).isEmpty();
    }

    @Test
    void getContractsForAppVersionAndMessageTypeOnTopicWithRole_contractDeleted_contractNotReturned() {
        //given
        List<MessageContract> contracts = List.of(createContract("app1", "v1", "commitHash", null, MessageContractRole.CONSUMER, null));
        messageContractRepository.saveContracts(contracts);
        assertThat(messageContractRepository.getContractsForAppVersionAndMessageTypeOnTopicWithRole("app1", "v1", "TestType", "topic", MessageContractRole.CONSUMER)).hasSize(1);

        //when
        messageContractRepository.deleteContract("app1", "v1", "TestType", "1.0.0", "topic", MessageContractRole.CONSUMER);

        //then
        assertThat(messageContractRepository.getContractsForAppVersionAndMessageTypeOnTopicWithRole("app1", "v1", "TestType", "topic", MessageContractRole.CONSUMER)).isEmpty();
    }

    private MessageContract createContract(String appName, String appVersion, String commitHash, String branch, MessageContractRole role, String encryptionKeyId) {
        return createContract(appName, appVersion, commitHash, branch, role, encryptionKeyId, "TestType", null);
    }

    private MessageContract createContract(String appName, String appVersion, String commitHash, String branch, MessageContractRole role, String encryptionKeyId, String messageType, String transactionId) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(messageType)
                .messageTypeVersion("1.0.0")
                .topic("topic")
                .role(role)
                .registryUrl("https://git/repo")
                .commitHash(commitHash)
                .branch(branch)
                .compatibilityMode(CompatibilityMode.BACKWARD)
                .avroProtocolSchema("{}")
                .encryptionKeyId(encryptionKeyId)
                .transactionId(transactionId)
                .build();
    }
}
