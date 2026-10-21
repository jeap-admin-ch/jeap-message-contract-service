package ch.admin.bit.jeap.messagecontract.persistence;

import java.util.UUID;

/**
 * The avro schemas stored for a message contract.
 */
public record MessageContractSchema(UUID id, String avroProtocolSchema, String avroSchema) {
}
