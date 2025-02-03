package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;

public record MessageTypeSchema(String messageTypeName, String avroProtocol) {

    public static MessageTypeSchema fromMessageContract(MessageContract messageContract) {
        return new MessageTypeSchema(messageContract.getMessageType(), messageContract.getAvroProtocolSchema());
    }
}
