package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import lombok.NonNull;
import lombok.Value;

@Value
public class MessageTypeVersion {
    @NonNull
    String version;
    String keySchema;
    @NonNull String valueSchema;
}
