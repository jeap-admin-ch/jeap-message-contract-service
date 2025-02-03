package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.web.api.dto.DeploymentDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentControllerTest extends ControllerTestBase {

    @Autowired
    private JpaDeploymentRepository deploymentRepository;

    @Autowired
    private JpaMessageContractRepository messageContractRepository;

    @AfterEach
    void cleanup() {
        deploymentRepository.deleteAll();
        messageContractRepository.deleteAll();
    }

    @Test
    void get_deployments() {
        webTestClient.get()
                .uri("/api/deployments")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Test
    void put_deployment_unauthorized() {
        webTestClient.put()
                .uri("/api/deployments/test/test/abn")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void put_deployment_appNotDefined() {
        webTestClient.put()
                .uri("/api/deployments/{appName}/{appVersion}/{environment}", "appUnknown", "appVersion", "prod")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(200);

        webTestClient.get()
                .uri("/api/deployments")
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBodyList(DeploymentDto.class)
                .value(dtos -> assertThat(dtos).isEmpty());
    }

    @Test
    void put_deployment_appDefined() {

        messageContractRepository.save(createContract("myAppName", "appVersion", "test", "test", MessageContractRole.CONSUMER));

        webTestClient.put()
                .uri("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "appVersion", "prod")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(201);

        webTestClient.get()
                .uri("/api/deployments")
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBodyList(DeploymentDto.class)
                .value(dtos -> {
                    assertThat(dtos).hasSize(1);
                    assertThat(dtos.get(0).appName()).isEqualTo("myAppName");
                });
    }

    @Test
    void delete_deployment_unauthorized() {
        webTestClient.delete()
                .uri("/api/deployments/test/abn")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void delete_deployment_appDefined() {

        messageContractRepository.save(createContract("myAppName", "appVersion", "test", "test", MessageContractRole.CONSUMER));

        webTestClient.put()
                .uri("/api/deployments/{appName}/{appVersion}/{environment}", "myAppName", "appVersion", "prod")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(201);

        webTestClient.delete()
                .uri("/api/deployments/myAppName/prod")
                .headers(headers -> headers.setBasicAuth("write", "secret"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);

        webTestClient.get()
                .uri("/api/deployments")
                .exchange()
                .expectStatus()
                .isEqualTo(200)
                .expectBodyList(DeploymentDto.class)
                .value(dtos -> {
                    assertThat(dtos).hasSize(0);
                });
    }

    private MessageContract createContract(String appName, String appVersion, String commitHash, String branch, MessageContractRole role) {
        return MessageContract.builder()
                .appName(appName)
                .appVersion(appVersion)
                .messageType("TestType")
                .messageTypeVersion("1.0.0")
                .topic("topic")
                .role(role)
                .registryUrl("https://git/repo")
                .commitHash(commitHash)
                .branch(branch)
                .compatibilityMode(CompatibilityMode.BACKWARD)
                .avroProtocolSchema("{}")
                .build();
    }

}
