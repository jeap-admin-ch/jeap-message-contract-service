package ch.admin.bit.jeap.messagecontract.web.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ControllerTestBase {

    protected final MockMvc mockMvc;

    @Autowired
    public ControllerTestBase(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

}
