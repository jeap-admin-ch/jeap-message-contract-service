package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MessageTypeRepositoryConfiguration.class)
@TestPropertySource(properties = {
        "messages.repositories[0].uri=https://example.invalid/contract.git",
        "messages.repositories[0].type=NONE",
        "messages.repository-cache.enabled=true",
        "messages.repository-cache.directory=/tmp/contract-cache-test",
        "messages.repository-cache.refresh-cron=0 30 2 * * *"
})
@SuppressWarnings("java:S5443")
class MessageTypeRepositoryConfigurationTest {

    private final MessageTypeRepositoryProperties repositoryProperties;
    private final MessageTypeRepositoryCacheProperties cacheProperties;

    @Autowired
    MessageTypeRepositoryConfigurationTest(MessageTypeRepositoryProperties repositoryProperties,
                                           MessageTypeRepositoryCacheProperties cacheProperties) {
        this.repositoryProperties = repositoryProperties;
        this.cacheProperties = cacheProperties;
    }

    @Test
    void repositoryCacheAndRepositoryPropertiesBindUnderMessagesPrefix() {
        assertThat(repositoryProperties.getRepositories()).hasSize(1);
        assertThat(repositoryProperties.getRepositories().getFirst().getUri())
                .isEqualTo("https://example.invalid/contract.git");

        assertThat(cacheProperties.isEnabled()).isTrue();
        assertThat(cacheProperties.getDirectory()).isEqualTo("/tmp/contract-cache-test");
        assertThat(cacheProperties.getRefreshCron()).isEqualTo("0 30 2 * * *");
    }
}
