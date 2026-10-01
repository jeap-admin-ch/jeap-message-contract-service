package ch.admin.bit.jeap.messagecontract.web.api.dto;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewMessageContractDtoTest {

    private static final String SCHEMA_JSON = """
            {"type":"record","name":"ActivZoneEnteredEvent","namespace":"ch.admin.ezv.activ",
             "fields":[{"name":"zoneId","type":"string"}]}""";

    @Test
    void toNewDomainObject_whenSchemaIsBlank_thenAvroSchemaIsNull() {
        MessageContract messageContract = contractDto("   ").toNewDomainObject("app", "1.0.0", null);

        assertThat(messageContract.getAvroSchema()).isNull();
        assertThat(messageContract.hasUploadedSchema()).isFalse();
    }

    @Test
    void toNewDomainObject_whenSchemaIsProvided_thenAvroSchemaIsSet() {
        MessageContract messageContract = contractDto(SCHEMA_JSON).toNewDomainObject("app", "1.0.0", null);

        assertThat(messageContract.getAvroSchema()).isEqualTo(SCHEMA_JSON);
        assertThat(messageContract.hasUploadedSchema()).isTrue();
    }

    @Test
    void toString_doesNotContainTheSchema() {
        String contractAsString = contractDto(SCHEMA_JSON).toString();

        assertThat(contractAsString)
                .doesNotContain("\"type\":\"record\"")
                .contains("ActivZoneEnteredEvent")
                .contains("schema=<" + SCHEMA_JSON.length() + " chars>");
    }

    private static NewMessageContractDto contractDto(String schema) {
        return new NewMessageContractDto("ActivZoneEnteredEvent", "1.0.0", "topic", MessageContractRole.CONSUMER,
                "registryUrl", "commitHash", "master", CompatibilityMode.BACKWARD, null, schema);
    }
}
