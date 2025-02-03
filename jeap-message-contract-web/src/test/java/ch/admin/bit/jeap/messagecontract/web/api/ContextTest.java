package ch.admin.bit.jeap.messagecontract.web.api;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;

@SpringBootTest
@Slf4j
class ContextTest {

    @Autowired
    private ContractController contractController;

    @Test
    void contextLoads() {
        assertNotNull(contractController);
    }
}
