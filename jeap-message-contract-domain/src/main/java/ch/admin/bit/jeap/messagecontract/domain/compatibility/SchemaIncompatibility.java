package ch.admin.bit.jeap.messagecontract.domain.compatibility;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.avro.Schema;

public record SchemaIncompatibility(String incompatibilityType,
                                    String message,
                                    @JsonIgnore Schema readerFragment,
                                    @JsonIgnore Schema writerFragment,
                                    String location) {
}
