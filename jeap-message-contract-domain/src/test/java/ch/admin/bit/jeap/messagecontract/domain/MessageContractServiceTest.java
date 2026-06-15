package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.PersistenceConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class})
@ExtendWith(MockitoExtension.class)
class MessageContractServiceTest {

    private static final String TEST_TYPE1 = "TestType1";
    private static final String TEST_TYPE2 = "TestType2";
    private static final String TEST_KEY = "testKey";
    private static final String TRANSACTION_ID = "transactionId";

    @MockitoBean
    private MessageTypeRepositoryFactory messageTypeRepositoryFactoryMock;

    @Mock
    private MessageTypeRepository messageTypeRepoMock;

    private final MessageContractService messageContractService;

    @Autowired
    MessageContractServiceTest(MessageContractService messageContractService) {
        this.messageContractService = messageContractService;
    }

    @Test
    void saveContracts() {
        when(messageTypeRepositoryFactoryMock.cloneRepository(any()))
                .thenReturn(messageTypeRepoMock);
        when(messageTypeRepoMock.getSchemaAsAvroProtocolJson(any(), any(), any(), any()))
                .thenReturn("{}");

        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", TEST_TYPE1, null),
                MessageContractTestFactory.createContract("app", "v1", TEST_TYPE2, TEST_KEY)
        );
        List<MessageContract> appV2Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v2", TEST_TYPE1, null),
                MessageContractTestFactory.createContract("app", "v2", TEST_TYPE2, TEST_KEY)
        );
        List<MessageContract> app2Contracts = List.of(
                MessageContractTestFactory.createContract("app2", "v1", TEST_TYPE1, null),
                MessageContractTestFactory.createContract("app2", "v1", TEST_TYPE2, TEST_KEY)
        );

        messageContractService.saveContracts("app", "v1", TRANSACTION_ID, appV1Contracts);
        messageContractService.saveContracts("app", "v2", TRANSACTION_ID, appV2Contracts);
        messageContractService.saveContracts("app2", "v1", TRANSACTION_ID, app2Contracts);
        assertEquals(6, messageContractService.getAllContracts().size());

        List<MessageContract> appV2UpdatedContracts = List.of(
                MessageContractTestFactory.createContract("app", "v2", TEST_TYPE1, null)
        );
        messageContractService.saveContracts("app", "v2", TRANSACTION_ID, appV2UpdatedContracts);
        assertEquals(5, messageContractService.getAllContracts().size());
        assertTrue(messageContractService.getAllContracts().stream().noneMatch(c ->
                c.getAppName().equals("app") && c.getAppVersion().equals("v2") && c.getMessageType().equals(TEST_TYPE2)));
    }


}
