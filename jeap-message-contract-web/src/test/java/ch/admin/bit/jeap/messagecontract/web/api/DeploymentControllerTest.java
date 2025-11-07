package ch.admin.bit.jeap.messagecontract.web.api;

import ch.admin.bit.jeap.messagecontract.persistence.JpaDeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.JpaMessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.CompatibilityMode;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.web.api.dto.DeploymentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerTest extends ControllerTestBase {

    @Autowired
    private JpaDeploymentRepository deploymentRepository;

    @Autowired
    private JpaMessageContractRepository messageContractRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        deploymentRepository.deleteAll();
        messageContractRepository.deleteAll();
    }

    @Test
    void getDeployments() throws Exception {
        mockMvc.perform(get("/api/deployments"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void putDeploymentUnauthorized() throws Exception {
        mockMvc.perform(put("/api/deployments/test/test/abn")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // 401
    }


    @Test
    void putDeploymentAppNotDefined() throws Exception {
        // PUT /api/deployments/{appName}/{appVersion}/{environment} with Basic Auth
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}", "appUnknown", "appVersion", "prod")
                        .header("Authorization", basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // 200

        // GET /api/deployments and assert empty list
        MvcResult result = mockMvc.perform(get("/api/deployments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
        );

        assertThat(dtos).isEmpty();
    }

    @Test
    void putDeploymentAppDefined() throws Exception {
        // Save a contract to set up the test
        messageContractRepository.save(createContract(
                "myAppName", "appVersion", "test", "test", MessageContractRole.CONSUMER));

        // PUT /api/deployments/{appName}/{appVersion}/{environment} with Basic Auth
        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}",
                        "myAppName", "appVersion", "prod")
                        .header("Authorization", basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()); // 201

        // GET /api/deployments and assert the list contains one deployment
        MvcResult result = mockMvc.perform(get("/api/deployments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
        );

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().appName()).isEqualTo("myAppName");
    }

    @Test
    void deleteDeploymentUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/deployments/test/abn"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    void deleteDeploymentAppDefined() throws Exception {
        // Save a contract to set up the test
        messageContractRepository.save(createContract(
                "myAppName", "appVersion", "test", "test", MessageContractRole.CONSUMER));

        // PUT /api/deployments/{appName}/{appVersion}/{environment} with Basic Auth
        String basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("write:secret").getBytes());

        mockMvc.perform(put("/api/deployments/{appName}/{appVersion}/{environment}",
                        "myAppName", "appVersion", "prod")
                        .header("Authorization", basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()); // 201

        // DELETE /api/deployments/{appName}/{environment} with Basic Auth
        mockMvc.perform(delete("/api/deployments/{appName}/{environment}", "myAppName", "prod")
                        .header("Authorization", basicAuthHeader))
                .andExpect(status().isOk()); // 200

        // GET /api/deployments and assert the list is empty
        MvcResult result = mockMvc.perform(get("/api/deployments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
        );

        assertThat(dtos).isEmpty();
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
