package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.ConsumerProducerInteraction;
import ch.admin.bit.jeap.messagecontract.domain.compatibility.CompatibilityCheckResult.Incompatibility;
import org.apache.avro.Schema;

import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Builds a human-readable error message for a list of message type schema incompatibilities
 */
class IncompatibilityMessageBuilder {

    public static String build(List<Incompatibility> incompatibilities) {
        if (incompatibilities.isEmpty()) {
            return "No incompatible interactions found";
        }

        return incompatibilities.stream()
                .map(IncompatibilityMessageBuilder::describe)
                .collect(joining("\n"));
    }

    private static String describe(Incompatibility incompatibility) {
        String source = describe(incompatibility.source());
        String target = describe(incompatibility.target());
        String incompatibilities = describe(incompatibility.schemaIncompatibilities());
        return """
                ***
                %s, which is incompatible with:
                - %s
                  List of schema incompatibilities:
                %s""".formatted(source, target, incompatibilities);
    }

    private static String describe(ConsumerProducerInteraction interaction) {
        return "App %s:%s is %s message type %s:%s on topic %s".formatted(
                interaction.appName(),
                interaction.appVersion(),
                interaction.role().verb(),
                interaction.messageType(),
                interaction.messageTypeVersion(),
                interaction.topic());
    }

    private static String describe(List<SchemaIncompatibility> schemaIncompatibilities) {
        return schemaIncompatibilities.stream()
                .map(schemaIncompatibility -> "  - " + describe(schemaIncompatibility))
                .collect(joining("\n"));
    }

    private static String describe(SchemaIncompatibility schemaIncompatibility) {
        return """
                %s at %s: %s
                    Schema Fragments (check type name for incompatible type):
                    - Reader: %s
                    - Writer: %s""".formatted(
                schemaIncompatibility.incompatibilityType(),
                schemaIncompatibility.location(),
                schemaIncompatibility.message(),
                toStringOrNoneIfNull(schemaIncompatibility.readerFragment()),
                toStringOrNoneIfNull(schemaIncompatibility.writerFragment()));
    }

    private static String toStringOrNoneIfNull(Schema schema) {
        return schema == null ? "<none>" : schema.toString();
    }
}
