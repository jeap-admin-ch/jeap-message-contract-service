package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryProperties;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCompatibilityServiceTest {

    private static TestRegistryRepo testRepo;
    private static String repoUrl;
    private SchemaCompatibilityService compatibilityService;
    private MessageTypeSchema activZoneEnteredEventV1;
    private MessageTypeSchema activZoneEnteredEventV2;

    @BeforeAll
    static void prepareRepository() throws Exception {
        testRepo = TestRegistryRepo.createMessageTypeRegistryRepository();
        repoUrl = testRepo.url();
    }

    @AfterAll
    static void deleteRepository() throws Exception {
        testRepo.delete();
    }

    @BeforeEach
    void setup() {
        compatibilityService = new SchemaCompatibilityService();
        try (MessageTypeRepository repo = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties()).cloneRepository(repoUrl)) {
            String avroProtocolJson1 = repo.getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            String avroProtocolJson2 = repo.getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "2.0.0");

            activZoneEnteredEventV1 = new MessageTypeSchema("ActivZoneEnteredEvent", avroProtocolJson1);
            activZoneEnteredEventV2 = new MessageTypeSchema("ActivZoneEnteredEvent", avroProtocolJson2);
        }
    }

    @Test
    void validateCompatibility() {
        List<SchemaIncompatibility> v1ComparedToV1 = compatibilityService.validateCompatibility(activZoneEnteredEventV1, activZoneEnteredEventV1);
        List<SchemaIncompatibility> v1ComparedToV2 = compatibilityService.validateCompatibility(activZoneEnteredEventV1, activZoneEnteredEventV2);

        assertThat(v1ComparedToV1).isEmpty();
        assertThat(v1ComparedToV2).isNotEmpty();
    }
}
