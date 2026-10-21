package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class MessageContractRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "data");
        registry.add("spring.flyway.default-schema", () -> "data");
    }

    @Autowired
    private MessageContractRepository messageContractRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsOnlyLatestDeploymentForEnvironmentAndNormalizedMessageTypeOnPostgres() {
        MessageContract oldContract = contract("app", "1.0.0", "TargetEvent");
        MessageContract currentContract = contract("app", "2.0.0", "TargetEvent");
        MessageContract otherType = contract("other", "1.0.0", "OtherEvent");
        messageContractRepository.saveContracts(List.of(oldContract, currentContract, otherType));
        ZonedDateTime currentDeploymentTime = ZonedDateTime.now();
        deploymentRepository.save(Deployment.builder().appName("app").appVersion("1.0.0").environment("PROD")
                .overrideCreatedAt(currentDeploymentTime.minusMinutes(1)).build());
        deploymentRepository.save(Deployment.builder().appName("app").appVersion("2.0.0").environment("PROD")
                .overrideCreatedAt(currentDeploymentTime).build());
        deploymentRepository.save(Deployment.builder().appName("other").appVersion("1.0.0").environment("PROD")
                .overrideCreatedAt(currentDeploymentTime).build());

        assertThat(messageContractRepository.findCurrentlyDeployedContracts("PROD", "targetevent"))
                .extracting(DeployedMessageContract::id)
                .containsExactly(currentContract.getId());
        assertThat(messageContractRepository.findCurrentlyDeployedContracts("REF", "targetevent")).isEmpty();
        assertThat(messageContractRepository.findSchemas(List.of(currentContract.getId())))
                .containsExactly(new MessageContractSchema(currentContract.getId(), "{}", null));
    }

    @Test
    void createsIndexesForTheCurrentlyDeployedContractsLookup() {
        List<String> indexDefinitions = jdbcTemplate.queryForList(
                "select indexdef from pg_indexes where schemaname = 'data'", String.class);

        assertThat(indexDefinitions)
                .anyMatch(definition -> definition.startsWith("CREATE INDEX message_contract_lower_message_type_not_deleted")
                        && definition.contains("(lower((message_type)::text))")
                        && definition.contains("WHERE (deleted = false)"))
                .anyMatch(definition -> definition.startsWith("CREATE INDEX deployment_app_name_environment_created_at")
                        && definition.contains("(app_name, environment, created_at DESC)"));
    }

    private static MessageContract contract(String appName, String appVersion, String messageType) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType(messageType)
                .messageTypeVersion("1.0.0")
                .topic("topic")
                .role(MessageContractRole.PRODUCER)
                .registryUrl("registry")
                .branch("master")
                .avroProtocolSchema("{}")
                .compatibilityMode(CompatibilityMode.NONE)
                .build();
    }
}
