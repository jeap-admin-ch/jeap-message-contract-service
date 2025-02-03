package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.ConsumerProducerInteraction;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.Incompatibility;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.InteractionRole;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncompatibilityMessageBuilderTest {

    @Test
    void build_noIncompatibilities() {
        String msg = IncompatibilityMessageBuilder.build(List.of());

        assertThat(msg)
                .isEqualTo("No incompatible interactions found");
    }

    @Test
    void build_hasIncompatibilities() {
        Schema readerSchema = Schema.createEnum("reader", null, null, List.of("value"));
        Schema writerSchema = Schema.createEnum("writer", null, null, List.of("value"));

        List<SchemaIncompatibility> schemaIncompatibilities = List.of(
                new SchemaIncompatibility("TYPE1", "description1", readerSchema, writerSchema, "/test1"),
                new SchemaIncompatibility("TYPE2", "description2", readerSchema, writerSchema, "/test2"));
        String msg = IncompatibilityMessageBuilder.build(List.of(
                new Incompatibility(
                        createInteraction("source", "msg-v1", InteractionRole.PRODUCER),
                        createInteraction("app1", "msg-v2", InteractionRole.CONSUMER),
                        schemaIncompatibilities),
                new Incompatibility(
                        createInteraction("source", "msg-v1", InteractionRole.PRODUCER),
                        createInteraction("app2", "msg-v2", InteractionRole.CONSUMER),
                        schemaIncompatibilities)));

        assertThat(msg)
                .isEqualTo("""
                        ***
                        App source:app-v1 is producing message type msg:msg-v1 on topic msg-topic, which is incompatible with:
                        - App app1:app-v1 is consuming message type msg:msg-v2 on topic msg-topic
                          List of schema incompatibilities:
                          - TYPE1 at /test1: description1
                            Schema Fragments (check type name for incompatible type):
                            - Reader: {"type":"enum","name":"reader","symbols":["value"]}
                            - Writer: {"type":"enum","name":"writer","symbols":["value"]}
                          - TYPE2 at /test2: description2
                            Schema Fragments (check type name for incompatible type):
                            - Reader: {"type":"enum","name":"reader","symbols":["value"]}
                            - Writer: {"type":"enum","name":"writer","symbols":["value"]}
                        ***
                        App source:app-v1 is producing message type msg:msg-v1 on topic msg-topic, which is incompatible with:
                        - App app2:app-v1 is consuming message type msg:msg-v2 on topic msg-topic
                          List of schema incompatibilities:
                          - TYPE1 at /test1: description1
                            Schema Fragments (check type name for incompatible type):
                            - Reader: {"type":"enum","name":"reader","symbols":["value"]}
                            - Writer: {"type":"enum","name":"writer","symbols":["value"]}
                          - TYPE2 at /test2: description2
                            Schema Fragments (check type name for incompatible type):
                            - Reader: {"type":"enum","name":"reader","symbols":["value"]}
                            - Writer: {"type":"enum","name":"writer","symbols":["value"]}""");
    }

    private static ConsumerProducerInteraction createInteraction(String appName, String messageTypeVersion, InteractionRole role) {
        return new ConsumerProducerInteraction(
                appName, "app-v1",
                "msg", messageTypeVersion,
                "msg-topic", role);
    }
}
