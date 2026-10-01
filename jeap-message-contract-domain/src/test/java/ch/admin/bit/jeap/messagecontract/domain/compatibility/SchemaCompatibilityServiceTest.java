package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepository;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryFactory;
import ch.admin.bit.jeap.messagecontract.messagetype.repository.MessageTypeRepositoryProperties;
import ch.admin.bit.jeap.messagecontract.test.TestRegistryRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.avro.Protocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        try (MessageTypeRepository repo = new MessageTypeRepositoryFactory(new MessageTypeRepositoryProperties(), new SimpleMeterRegistry()).cloneRepository(repoUrl)) {
            String avroProtocolJson1 = repo.getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "1.0.0");
            String avroProtocolJson2 = repo.getSchemaAsAvroProtocolJson("master", null, "ActivZoneEnteredEvent", "2.0.0");

            activZoneEnteredEventV1 = new MessageTypeSchema("ActivZoneEnteredEvent", avroProtocolJson1, null);
            activZoneEnteredEventV2 = new MessageTypeSchema("ActivZoneEnteredEvent", avroProtocolJson2, null);
        }
    }

    @Test
    void validateCompatibility() {
        List<SchemaIncompatibility> v1ComparedToV1 = compatibilityService.validateCompatibility(activZoneEnteredEventV1, activZoneEnteredEventV1);
        List<SchemaIncompatibility> v1ComparedToV2 = compatibilityService.validateCompatibility(activZoneEnteredEventV1, activZoneEnteredEventV2);

        assertThat(v1ComparedToV1).isEmpty();
        assertThat(v1ComparedToV2).isNotEmpty();
    }

    @Test
    void validateCompatibility_whenSchemaUploadedAsAvroRecordSchema_thenCompatibleWithProtocolFromRegistry() {
        // An uploaded schema is the avro record schema (as found in the SCHEMA$ field of a generated message type),
        // whereas a schema read from the message type registry is an avro protocol
        MessageTypeSchema uploadedV1 = toRecordSchema(activZoneEnteredEventV1);
        MessageTypeSchema uploadedV2 = toRecordSchema(activZoneEnteredEventV2);

        assertThat(compatibilityService.validateCompatibility(uploadedV1, uploadedV1)).isEmpty();
        assertThat(compatibilityService.validateCompatibility(uploadedV1, activZoneEnteredEventV1)).isEmpty();
        assertThat(compatibilityService.validateCompatibility(activZoneEnteredEventV1, uploadedV1)).isEmpty();
        assertThat(compatibilityService.validateCompatibility(uploadedV1, uploadedV2)).isNotEmpty();
        assertThat(compatibilityService.validateCompatibility(activZoneEnteredEventV1, uploadedV2)).isNotEmpty();
    }

    @Test
    void validateCompatibility_whenNoSchemaAvailable_thenThrowsIllegalStateException() {
        MessageTypeSchema noSchema = new MessageTypeSchema("ActivZoneEnteredEvent", null, null);

        assertThatThrownBy(() -> compatibilityService.validateCompatibility(noSchema, activZoneEnteredEventV1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ActivZoneEnteredEvent");
    }

    private static MessageTypeSchema toRecordSchema(MessageTypeSchema protocolSchema) {
        String recordSchemaJson = Protocol.parse(protocolSchema.avroProtocol())
                .getType(protocolSchema.messageTypeName())
                .toString();
        return new MessageTypeSchema(protocolSchema.messageTypeName(), null, recordSchemaJson);
    }
}
