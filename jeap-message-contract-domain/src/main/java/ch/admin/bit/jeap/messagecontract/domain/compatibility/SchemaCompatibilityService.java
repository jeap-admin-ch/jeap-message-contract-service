package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityResult;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchemaCompatibilityService {

    public List<SchemaIncompatibility> validateCompatibility(MessageContract contract, MessageContract interactedWithContract) {
        if (contract.getRole() == MessageContractRole.CONSUMER) {
            return validateReaderWriterCompatibility(contract, interactedWithContract);
        }
        return validateReaderWriterCompatibility(interactedWithContract, contract);
    }

    public List<SchemaIncompatibility> validateReaderWriterCompatibility(MessageContract readerContract,
                                                                          MessageContract writerContract) {
        return validateCompatibility(
                MessageTypeSchema.fromMessageContract(readerContract),
                MessageTypeSchema.fromMessageContract(writerContract));
    }

    List<SchemaIncompatibility> validateCompatibility(MessageTypeSchema readerSchema, MessageTypeSchema writerSchema) {
        Schema readerAvroSchema = getAvroSchema(readerSchema);
        Schema writerAvroSchema = getAvroSchema(writerSchema);

        SchemaCompatibilityResult result = SchemaCompatibility.checkReaderWriterCompatibility(
                readerAvroSchema, writerAvroSchema).getResult();

        if (result.getCompatibility() != SchemaCompatibilityType.COMPATIBLE) {
            return result.getIncompatibilities().stream()
                    .map(this::convert)
                    .toList();
        }
        return List.of();
    }

    private SchemaIncompatibility convert(SchemaCompatibility.Incompatibility incompatibility) {
        return new SchemaIncompatibility(
                incompatibility.getType().name(),
                incompatibility.getMessage(),
                incompatibility.getReaderFragment(),
                incompatibility.getWriterFragment(),
                incompatibility.getLocation());
    }

    private Schema getAvroSchema(MessageTypeSchema schema) {
        if (schema.avroSchema() != null && !schema.avroSchema().isBlank()) {
            return new Schema.Parser().parse(schema.avroSchema());
        }
        if (schema.avroProtocol() == null) {
            throw new IllegalStateException("No avro schema available for message type " + schema.messageTypeName());
        }
        Protocol protocol = Protocol.parse(schema.avroProtocol());
        return protocol.getType(schema.messageTypeName());
    }
}
