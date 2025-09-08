package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubMessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTypeRepositoryTest {

    private TestRegistryRepo repo;
    private String repoUrl;

    @BeforeEach
    void prepareRepository() throws Exception {
        repo = TestRegistryRepo.createMessageTypeRegistryRepository();
        repoUrl = repo.url();
    }

    @AfterEach
    void deleteRepository() throws Exception {
        repo.delete();
    }

    @Test
    void getSchemaAsAvroProtocolJson() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromBranch1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");

            String schemaJsonFromCommit1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromCommit1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");

            String schemaJson2 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "2.0.0");
            assertTrue(schemaJson2.contains("JourneyActivationRequestReference"), "JourneyActivationRequestReference (only present in 2.0.0) not found");
        }
    }

    @Test
    void getSchemaAsAvroProtocolJson_whenNotFound_thenExpectException() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "DoesNotExist", "1.0.0"));
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson("badBranch", null, "ActivZoneEnteredEvent", "1.0.0"));
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", "unknownCommit", "ActivZoneEnteredEvent", "1.0.0"));
        }
    }

    @Test
    void addBadlyFormattedDescriptor_expectRepositoryIsReadWithoutIt() throws Exception {
        Path badEventDescriptor = repo.repoDir().resolve("descriptor/activ/event/badevent/BadEvent.json");
        repo.addAndCommitFile(badEventDescriptor, "this is not really a json document");

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {

            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromBranch1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");
        }
    }

    @Test
    void clonesRepo_badUrl() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties());
        assertThrows(MessageTypeRepoException.class, () ->
                factory.cloneRepository("file:///this-does-not-exist"));
    }

    @Test
    void messageTypeRepositoryInitializesGitHubWhenKnown() {
        MessageTypeRepositoryProperties messageTypeRepositoryProperties = new MessageTypeRepositoryProperties();
        RepositoryProperties properties = new RepositoryProperties();
        properties.setUri(repoUrl);
        properties.setType(RepositoryProperties.RepositoryType.GITHUB);
        // Sample key from https://phpseclib.com/docs/rsa-keys
        properties.setParameters(Map.of("GITHUB_APP_ID", "12345", "GITHUB_PRIVATE_KEY_PEM", """
                -----BEGIN RSA PRIVATE KEY-----
                MIIBOgIBAAJBAKj34GkxFhD90vcNLYLInFEX6Ppy1tPf9Cnzj4p4WGeKLs1Pt8Qu
                KUpRKfFLfRYC9AIKjbJTWit+CqvjWYzvQwECAwEAAQJAIJLixBy2qpFoS4DSmoEm
                o3qGy0t6z09AIJtH+5OeRV1be+N4cDYJKffGzDa88vQENZiRm0GRq6a+HPGQMd2k
                TQIhAKMSvzIBnni7ot/OSie2TmJLY4SwTQAevXysE2RbFDYdAiEBCUEaRQnMnbp7
                9mxDXDf6AU0cN/RPBjb9qSHDcWZHGzUCIG2Es59z8ugGrDY+pxLQnwfotadxd+Uy
                v/Ow5T0q5gIJAiEAyS4RaI9YG8EWx/2w0T67ZUVAw8eOMB6BIUg0Xcu+3okCIBOs
                /5OiPgoTdSy7bcF9IGpSE8ZgGKzgYQVZeN97YE00
                -----END RSA PRIVATE KEY-----
                """));
        messageTypeRepositoryProperties.setRepositories(Collections.singletonList(properties));
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(messageTypeRepositoryProperties);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            assertInstanceOf(GitHubMessageTypeRepository.class, messageTypeRepository);
            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromBranch1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");

            String schemaJsonFromCommit1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromCommit1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");

            String schemaJson2 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "2.0.0");
            assertTrue(schemaJson2.contains("JourneyActivationRequestReference"), "JourneyActivationRequestReference (only present in 2.0.0) not found");
        }
    }

    @Test
    @Disabled("Enable and configure it to test the GitHub integration")
    void messageTypeRepositoryInitializesAndClonesGitHubWhenKnown() {
        System.setProperty("http.proxyHost", "...");
        System.setProperty("https.proxyHost", "...");
        System.setProperty("http.proxyPort", "8080");
        System.setProperty("https.proxyPort", "8080");
        String githubRepoUrl = "https://github.com/.....git";
        MessageTypeRepositoryProperties messageTypeRepositoryProperties = new MessageTypeRepositoryProperties();
        RepositoryProperties properties = new RepositoryProperties();
        properties.setUri(githubRepoUrl);
        properties.setType(RepositoryProperties.RepositoryType.GITHUB);
        // Sample key from https://phpseclib.com/docs/rsa-keys
        properties.setParameters(Map.of("GITHUB_APP_ID", "...", "GITHUB_PRIVATE_KEY_PEM", """
                        -----BEGIN RSA PRIVATE KEY-----
                        ....
                        -----END RSA PRIVATE KEY-----"""));
        messageTypeRepositoryProperties.setRepositories(Collections.singletonList(properties));
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(messageTypeRepositoryProperties);

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(githubRepoUrl)) {
            assertInstanceOf(GitHubMessageTypeRepository.class, messageTypeRepository);
            Assertions.assertTrue(new File(messageTypeRepository.gitRepoPath, "README.md").exists());
        }
    }
}
