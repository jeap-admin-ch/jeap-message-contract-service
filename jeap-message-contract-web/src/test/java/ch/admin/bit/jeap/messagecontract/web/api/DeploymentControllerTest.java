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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerTest extends ControllerTestBase {

    private static final String AUTHORIZATION = "Authorization";
    private static final String WRITE_SECRET = "write:secret";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String API_DEPLOYMENTS = "/api/deployments";
    private static final String API_DEPLOYMENTS_APP_ENV = "/api/deployments/{appName}/{appVersion}/{environment}";
    private static final String MY_APP_NAME = "myAppName";
    private static final String APP_VERSION = "appVersion";

    private final JpaDeploymentRepository deploymentRepository;
    private final JpaMessageContractRepository messageContractRepository;
    private final JsonMapper jsonMapper;

    @Autowired
    DeploymentControllerTest(MockMvc mockMvc,
                              JpaDeploymentRepository deploymentRepository,
                              JpaMessageContractRepository messageContractRepository,
                              JsonMapper jsonMapper) {
        super(mockMvc);
        this.deploymentRepository = deploymentRepository;
        this.messageContractRepository = messageContractRepository;
        this.jsonMapper = jsonMapper;
    }

    @AfterEach
    void cleanup() {
        deploymentRepository.deleteAll();
        messageContractRepository.deleteAll();
    }

    @Test
    void getDeployments() throws Exception {
        mockMvc.perform(get(API_DEPLOYMENTS))
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
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder().encodeToString((WRITE_SECRET).getBytes());

        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV, "appUnknown", APP_VERSION, "prod")
                        .header(AUTHORIZATION, basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // 200

        // GET /api/deployments and assert empty list
        MvcResult result = mockMvc.perform(get(API_DEPLOYMENTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = jsonMapper.readValue(
                responseBody,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
        );

        assertThat(dtos).isEmpty();
    }

    @Test
    void putDeploymentAppDefined() throws Exception {
        // Save a contract to set up the test
        messageContractRepository.save(createContract(
                MY_APP_NAME, APP_VERSION, "test", "test", MessageContractRole.CONSUMER));

        // PUT /api/deployments/{appName}/{appVersion}/{environment} with Basic Auth
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder()
                .encodeToString((WRITE_SECRET).getBytes());

        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV,
                        MY_APP_NAME, APP_VERSION, "prod")
                        .header(AUTHORIZATION, basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()); // 201

        // GET /api/deployments and assert the list contains one deployment
        MvcResult result = mockMvc.perform(get(API_DEPLOYMENTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = jsonMapper.readValue(
                responseBody,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
        );

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().appName()).isEqualTo(MY_APP_NAME);
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
                MY_APP_NAME, APP_VERSION, "test", "test", MessageContractRole.CONSUMER));

        // PUT /api/deployments/{appName}/{appVersion}/{environment} with Basic Auth
        String basicAuthHeader = BASIC_PREFIX + Base64.getEncoder()
                .encodeToString((WRITE_SECRET).getBytes());

        mockMvc.perform(put(API_DEPLOYMENTS_APP_ENV,
                        MY_APP_NAME, APP_VERSION, "prod")
                        .header(AUTHORIZATION, basicAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()); // 201

        // DELETE /api/deployments/{appName}/{environment} with Basic Auth
        mockMvc.perform(delete("/api/deployments/{appName}/{environment}", MY_APP_NAME, "prod")
                        .header(AUTHORIZATION, basicAuthHeader))
                .andExpect(status().isOk()); // 200

        // GET /api/deployments and assert the list is empty
        MvcResult result = mockMvc.perform(get(API_DEPLOYMENTS)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<DeploymentDto> dtos = jsonMapper.readValue(
                responseBody,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, DeploymentDto.class)
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
