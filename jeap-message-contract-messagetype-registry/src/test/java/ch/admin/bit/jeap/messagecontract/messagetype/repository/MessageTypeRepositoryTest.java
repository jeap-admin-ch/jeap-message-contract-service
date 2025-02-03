package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory();
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
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory();
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

        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory();
        try (MessageTypeRepository messageTypeRepository = factory.cloneRepository(repoUrl)) {

            String schemaJsonFromBranch1 = messageTypeRepository
                    .getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            assertTrue(schemaJsonFromBranch1.contains("ZoneReference"), "ZoneReference (only present in 1.0.0) not found");
        }
    }

    @Test
    void clonesRepo_badUrl() {
        MessageTypeRepositoryFactory factory = new MessageTypeRepositoryFactory();
        assertThrows(MessageTypeRepoException.class, () ->
                factory.cloneRepository("file:///this-does-not-exist"));
    }
}