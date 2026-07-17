package ch.admin.bit.jeap.messagecontract.web.api;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RenovateControllerSecurityTest extends ControllerTestBase {

    @Autowired
    RenovateControllerSecurityTest(MockMvc mockMvc) {
        super(mockMvc);
    }

    @Test
    @SneakyThrows
    void readUserCanQueryRenovateDatasource() {
        mockMvc.perform(get("/api/renovate/message-types/{packageName}",
                        "ch.admin.bit.jeap.messagetype.test:test-event")
                        .param("currentValue", "1.0.0")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    void readUserCannotUploadContracts() {
        mockMvc.perform(put("/api/contracts/{appName}/{appVersion}", "test-app", "1.0.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/contracts/app/1.0.0")
                        .param("messageType", "TestEvent")
                        .param("messageTypeVersion", "1.0.0")
                        .param("topic", "topic")
                        .param("role", "PRODUCER")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    @SneakyThrows
    void unauthenticatedRequestIsUnauthorized() {
        mockMvc.perform(renovateRequest()).andExpect(status().isUnauthorized());
    }

    @Test
    @SneakyThrows
    void invalidAuthenticationIsUnauthorized() {
        mockMvc.perform(renovateRequest().with(httpBasic("read", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SneakyThrows
    void unrelatedRoleIsForbidden() {
        mockMvc.perform(renovateRequest().with(user("other").roles("unrelated")))
                .andExpect(status().isForbidden());
    }

    @Test
    @SneakyThrows
    void readUserCannotRegisterOrDeleteDeployments() {
        mockMvc.perform(put("/api/deployments/app/1.0.0/PROD").with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/deployments/app/PROD").with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder renovateRequest() {
        return get("/api/renovate/message-types/{packageName}",
                "ch.admin.bit.jeap.messagetype.test:test-event").param("currentValue", "1.0.0");
    }
}
