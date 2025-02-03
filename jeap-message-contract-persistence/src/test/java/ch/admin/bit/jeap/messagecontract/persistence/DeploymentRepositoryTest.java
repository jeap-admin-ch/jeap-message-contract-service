package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.locations=classpath:db/migration/common",
        "spring.datasource.url=jdbc:h2:mem:db;MODE=PostgreSQL"
})
@ContextConfiguration(classes = PersistenceConfiguration.class)
class DeploymentRepositoryTest {

    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void save() {
        Deployment deployment = Deployment.builder()
                .appName("test")
                .appVersion("123")
                .environment("ABN")
                .build();

        deploymentRepository.save(deployment);
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc()).hasSize(1);
    }

    @Test
    void findTop10ByOrderByCreatedAt() {
        createAndSave15AppDeployments();
        List<Deployment> deployments = deploymentRepository.findTop10ByOrderByCreatedAtDesc();
        assertThat(deployments).hasSize(10);
        assertThat(deployments.get(0).getAppName()).isEqualTo("app14");
        assertThat(deployments.get(9).getAppName()).isEqualTo("app5");
    }

    @Test
    void findAppVersionCurrentlyDeployedOnEnvironment() {
        UUID newestDeploymentId = createAndSaveDeployment("app1", "v2");
        createAndSaveDeployment("app1", "v1");
        createAndSaveDeployment("app2", "v3");
        testEntityManager.getEntityManager()
                .createNativeQuery("UPDATE deployment d SET d.created_at = d.created_at + interval '1' day WHERE d.id='%s'".formatted(newestDeploymentId))
                .executeUpdate();
        testEntityManager.flush();

        Optional<String> version = deploymentRepository.findAppVersionCurrentlyDeployedOnEnvironment("app1", "ABN");
        assertThat(version).isPresent();
        assertThat(version.get()).isEqualTo("v2");

        Optional<String> notDeployedVersion = deploymentRepository.findAppVersionCurrentlyDeployedOnEnvironment("app1", "REF");
        assertThat(notDeployedVersion).isNotPresent();
    }

    @Test
    void findNewestDeploymentPerAppAndEnv() {
        deploymentRepository.save(Deployment.builder()
                .appName("app1")
                .appVersion("1.0")
                .environment("ABN")
                .overrideCreatedAt(ZonedDateTime.now().plusDays(1))
                .build());
        // Newest deployment of app1 on ABN
        Deployment app1Abn = deploymentRepository.save(Deployment.builder()
                .appName("app1")
                .appVersion("2.0")
                .environment("ABN")
                .overrideCreatedAt(ZonedDateTime.now().plusDays(2))
                .build());
        // Newest deployment of app1 on DEV
        Deployment app1Dev = deploymentRepository.save(Deployment.builder()
                .appName("app1")
                .appVersion("3.0")
                .environment("DEV")
                .overrideCreatedAt(ZonedDateTime.now().plusDays(4))
                .build());
        deploymentRepository.save(Deployment.builder()
                .appName("app1")
                .appVersion("4.0")
                .environment("DEV")
                .overrideCreatedAt(ZonedDateTime.now().plusDays(3))
                .build());
        // Newest deployment of app2 on ABN
        Deployment app2Abn = deploymentRepository.save(Deployment.builder()
                .appName("app2")
                .appVersion("2.0")
                .environment("ABN")
                .overrideCreatedAt(ZonedDateTime.now().plusDays(5))
                .build());

        List<Deployment> deployments = deploymentRepository.findNewestDeploymentPerAppAndEnv();

        assertThat(deployments)
                .containsExactly(app1Abn, app1Dev, app2Abn);
    }

    @Test
    public void deleteDeployment() {
        createAndSaveDeployment("app", "1");
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(1);

        deploymentRepository.deleteDeployment("app", "ABN");
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(0);
    }

    @Test
    public void deleteDeploymentDeletesOnlyApp() {
        createAndSaveDeployment("app", "1");
        createAndSaveDeployment("app1", "1");
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(2);

        deploymentRepository.deleteDeployment("app", "ABN");
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(1);
    }

    @Test
    public void deleteDeploymentDeletesOnlyGivenEnv() {
        Deployment deployment = Deployment.builder()
                .appName("app")
                .appVersion("1")
                .environment("ABN")
                .build();
        deploymentRepository.save(deployment);
        Deployment deploymentRef = Deployment.builder()
                .appName("app")
                .appVersion("1")
                .environment("REF")
                .build();
        deploymentRepository.save(deploymentRef);
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(2);

        deploymentRepository.deleteDeployment("app", "ABN");
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().size()).isEqualTo(1);
        assertThat(deploymentRepository.findTop10ByOrderByCreatedAtDesc().get(0).getEnvironment()).isEqualTo("REF");
    }

    private void createAndSave15AppDeployments() {
        for (int i = 0; i < 15; i++) {
            createAndSaveDeployment("app" + i, "123");
        }
    }

    private UUID createAndSaveDeployment(String appName, String appVersion) {
        Deployment deployment = Deployment.builder()
                .appName(appName)
                .appVersion(appVersion)
                .environment("ABN")
                .build();
        return deploymentRepository.save(deployment).getId();
    }
}
