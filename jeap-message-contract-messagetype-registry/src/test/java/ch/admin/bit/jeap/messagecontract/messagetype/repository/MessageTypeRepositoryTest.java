package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.github.GitHubMessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTypeRepositoryTest {

    private static final String MASTER = "master";
    private static final String ACTIV_ZONE_ENTERED_EVENT = "ActivZoneEnteredEvent";
    private static final String VERSION_1_0_0 = "1.0.0";
    private static final String VERSION_2_0_0 = "2.0.0";
    private static final String ZONE_REFERENCE = "ZoneReference";
    private static final String ZONE_REFERENCE_NOT_FOUND = "ZoneReference (only present in 1.0.0) not found";
    private static final String NEW_EVENT = "NewEvent";
    private static final String FEATURE = "feature";

    private static final String NEW_EVENT_DESCRIPTOR_JSON = """
            {
              "eventName": "NewEvent",
              "description": "New event for tests",
              "publishingSystem": "ACTIV",
              "scope": "public",
              "topic": "activ-new-event",
              "compatibilityMode": "BACKWARD",
              "versions": [
                {"version": "1.0.0", "valueSchema": "NewEvent_v1.0.0.avdl"}
              ],
              "contracts": {"publishers": [], "subscribers": []}
            }
            """;

    private static final String NEW_EVENT_AVDL = """
            @namespace("ch.admin.test")
            protocol NewEventProtocol {
                record NewEvent {
                    string attribute;
                }
            }
            """;

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
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJsonFromBranch1.contains(ZONE_REFERENCE), ZONE_REFERENCE_NOT_FOUND);

            String schemaJsonFromCommit1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJsonFromCommit1.contains(ZONE_REFERENCE), ZONE_REFERENCE_NOT_FOUND);

            String schemaJson2 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_2_0_0);
            assertTrue(schemaJson2.contains("JourneyActivationRequestReference"), "JourneyActivationRequestReference (only present in 2.0.0) not found");
        }
    }

    @Test
    void getSchemaAsAvroProtocolJsonWhenNotFoundThenExpectException() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, "DoesNotExist", VERSION_1_0_0));
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson("badBranch", null, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0));
            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, "unknownCommit", ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0));
        }
    }

    @Test
    void addBadlyFormattedDescriptorExpectRepositoryIsReadWithoutIt() throws Exception {
        Path badEventDescriptor = repo.repoDir().resolve("descriptor/activ/event/badevent/BadEvent.json");
        repo.addAndCommitFile(badEventDescriptor, "this is not really a json document");

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {

            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJsonFromBranch1.contains(ZONE_REFERENCE), ZONE_REFERENCE_NOT_FOUND);
        }
    }

    @Test
    void clonesRepoBadUrl() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
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
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(messageTypeRepositoryProperties, new SimpleMeterRegistry());

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            assertInstanceOf(GitHubMessageTypeRepository.class, messageTypeRepository);
            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJsonFromBranch1.contains(ZONE_REFERENCE), ZONE_REFERENCE_NOT_FOUND);

            String schemaJsonFromCommit1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, repo.revision(), ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJsonFromCommit1.contains(ZONE_REFERENCE), ZONE_REFERENCE_NOT_FOUND);

            String schemaJson2 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_2_0_0);
            assertTrue(schemaJson2.contains("JourneyActivationRequestReference"), "JourneyActivationRequestReference (only present in 2.0.0) not found");
        }
    }

    @Test
    void cloneIsShallowOnlyOneCommitInLocalRepo() throws Exception {
        Path extra = repo.repoDir().resolve("descriptor/activ/event/extra/Extra.json");
        repo.addAndCommitFile(extra, "{}");

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            try (Git localClone = Git.open(messageTypeRepository.gitRepoPath);
                 RevWalk walk = new RevWalk(localClone.getRepository())) {
                ObjectId head = localClone.getRepository().resolve("HEAD");
                walk.markStart(walk.parseCommit(head));
                int commitCount = 0;
                for (var _ : walk) {
                    commitCount++;
                }
                assertEquals(1, commitCount, "shallow clone should expose exactly one commit");
            }
        }
    }

    @Test
    void cloneDoesNotFetchTags() throws Exception {
        repo.tag("v1.0");

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            try (Git localClone = Git.open(messageTypeRepository.gitRepoPath)) {
                assertTrue(localClone.tagList().call().isEmpty(), "no tags should be fetched");
            }
        }
    }

    @Test
    void checkoutBranchSeesCommitPushedAfterClone() throws Exception {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            Path newEventDir = repo.repoDir().resolve("descriptor/activ/event/newevent");
            repo.addAndCommitFile(newEventDir.resolve("NewEvent_v1.0.0.avdl"), NEW_EVENT_AVDL);
            repo.addAndCommitFile(newEventDir.resolve("NewEvent.json"), NEW_EVENT_DESCRIPTOR_JSON);

            String schemaJson = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, NEW_EVENT, VERSION_1_0_0);

            assertTrue(schemaJson.contains(NEW_EVENT),
                    "new descriptor pushed after clone should be visible via lazy fetch");
        }
    }

    @Test
    void checkoutBranchNonDefaultBranch() throws Exception {
        repo.createBranch(FEATURE);
        Path newEventDir = repo.repoDir().resolve("descriptor/activ/event/newevent");
        repo.addAndCommitFileOnBranch(FEATURE, newEventDir.resolve("NewEvent_v1.0.0.avdl"), NEW_EVENT_AVDL);
        repo.addAndCommitFileOnBranch(FEATURE, newEventDir.resolve("NewEvent.json"), NEW_EVENT_DESCRIPTOR_JSON);

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaJson = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(FEATURE, null, NEW_EVENT, VERSION_1_0_0);
            assertTrue(schemaJson.contains(NEW_EVENT),
                    "feature branch descriptor should be reachable via per-checkout branch fetch");

            assertThrows(MessageTypeRepoException.class, () -> messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, null, NEW_EVENT, VERSION_1_0_0));
        }
    }

    @Test
    void checkoutCommitOlderCommitOutsideShallowDepth() throws Exception {
        String originalSha = repo.revision();
        repo.addAndCommitFile(repo.repoDir().resolve("descriptor/activ/event/extra/Extra.json"), "{}");
        repo.addAndCommitFile(repo.repoDir().resolve("descriptor/activ/event/extra/Extra2.json"), "{}");

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaJson = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, originalSha, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJson.contains(ZONE_REFERENCE),
                    "original commit's schema should be reachable via fetch-by-SHA even when outside the initial shallow depth");
        }
    }

    @Test
    void checkoutAtBothBranchAndCommitNullNoOpAndUsesClonedHead() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String schemaJson = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(null, null, ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(schemaJson.contains(ZONE_REFERENCE),
                    "should serve content already present in the shallow clone without an additional fetch");
        }
    }

    @Test
    void checkoutAtHeadLiteralAsCommitReferenceTakesBranchPath() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry());
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {
            String upper = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, "HEAD", ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(upper.contains(ZONE_REFERENCE));

            String lower = messageTypeRepository
                    .getSchemaAsAvroProtocolJson(MASTER, "head", ACTIV_ZONE_ENTERED_EVENT, VERSION_1_0_0);
            assertTrue(lower.contains(ZONE_REFERENCE));
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
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory(messageTypeRepositoryProperties, new SimpleMeterRegistry());

        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(githubRepoUrl)) {
            assertInstanceOf(GitHubMessageTypeRepository.class, messageTypeRepository);
            Assertions.assertTrue(new File(messageTypeRepository.gitRepoPath, "README.md").exists());
        }
    }
}
