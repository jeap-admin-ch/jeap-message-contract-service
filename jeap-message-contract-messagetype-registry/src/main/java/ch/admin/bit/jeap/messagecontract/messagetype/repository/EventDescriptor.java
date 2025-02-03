package ch.admin.bit.jeap.messagecontract.messagetype.repository;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Data
public class EventDescriptor implements MessageTypeDescriptor {
    public static final String SUBDIR = "event";

    @JsonAlias("eventName")
    @NonNull
    private String messageTypeName;
    private List<MessageTypeVersion> versions;
    private SchemaLocations schemaLocations;

    public List<MessageTypeVersion> getVersions() {
        return versions == null ? List.of() : versions;
    }

    @Override
    public void setSchemaLocations(SchemaLocations schemaLocations) {
        this.schemaLocations = schemaLocations;
    }

    @Override
    public SchemaLocations getSchemaLocations() {
        return schemaLocations;
    }
}
