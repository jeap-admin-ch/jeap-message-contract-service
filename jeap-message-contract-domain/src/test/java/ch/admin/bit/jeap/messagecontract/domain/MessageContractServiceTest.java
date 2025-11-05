package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.persistence.PersistenceConfiguration;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, DomainConfiguration.class})
class MessageContractServiceTest {

    @MockBean
    private MessageTypeRepositoryFactory messageTypeRepositoryFactoryMock;

    @Mock
    private MessageTypeRepository messageTypeRepoMock;

    @Autowired
    private MessageContractService messageContractService;

    @Test
    void saveContracts() {
        when(messageTypeRepositoryFactoryMock.cloneRepository(any()))
                .thenReturn(messageTypeRepoMock);
        when(messageTypeRepoMock.getSchemaAsAvroProtocolJson(any(), any(), any(), any()))
                .thenReturn("{}");

        List<MessageContract> appV1Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v1", "TestType1", null),
                MessageContractTestFactory.createContract("app", "v1", "TestType2", "testKey")
        );
        List<MessageContract> appV2Contracts = List.of(
                MessageContractTestFactory.createContract("app", "v2", "TestType1", null),
                MessageContractTestFactory.createContract("app", "v2", "TestType2", "testKey")
        );
        List<MessageContract> app2Contracts = List.of(
                MessageContractTestFactory.createContract("app2", "v1", "TestType1", null),
                MessageContractTestFactory.createContract("app2", "v1", "TestType2", "testKey")
        );

        messageContractService.saveContracts("app", "v1", "transactionId", appV1Contracts);
        messageContractService.saveContracts("app", "v2", "transactionId", appV2Contracts);
        messageContractService.saveContracts("app2", "v1", "transactionId", app2Contracts);
        assertEquals(6, messageContractService.getAllContracts().size());

        List<MessageContract> appV2UpdatedContracts = List.of(
                MessageContractTestFactory.createContract("app", "v2", "TestType1", null)
        );
        messageContractService.saveContracts("app", "v2", "transactionId", appV2UpdatedContracts);
        assertEquals(5, messageContractService.getAllContracts().size());
        assertTrue(messageContractService.getAllContracts().stream().noneMatch(c ->
                c.getAppName().equals("app") && c.getAppVersion().equals("v2") && c.getMessageType().equals("TestType2")));
    }


}
