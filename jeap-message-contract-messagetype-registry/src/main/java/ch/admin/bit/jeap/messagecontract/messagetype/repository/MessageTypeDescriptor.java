package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import java.util.List;
import java.util.Optional;

public interface MessageTypeDescriptor {

    String getMessageTypeName();

    List<MessageTypeVersion> getVersions();

    default Optional<MessageTypeVersion> findVersion(String messageTypeVersion) {
        return getVersions().stream()
                .filter(v -> v.getVersion().equals(messageTypeVersion))
                .findFirst();
    }

    void setSchemaLocations(SchemaLocations schemaLocations);

    SchemaLocations getSchemaLocations();
}
