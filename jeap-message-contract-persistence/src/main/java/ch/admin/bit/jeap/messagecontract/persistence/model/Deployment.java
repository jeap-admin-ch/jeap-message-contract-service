package ch.admin.bit.jeap.messagecontract.persistence.model;

import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.ZonedDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Deployment {
    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    @NonNull
    private String appName;

    @NonNull
    private String appVersion;

    @NonNull
    private String environment;

    private ZonedDateTime createdAt;

    @Builder
    private Deployment(String appName, String appVersion, String environment, ZonedDateTime overrideCreatedAt) {
        this.id = UUID.randomUUID();
        this.appName = appName;
        this.appVersion = appVersion;
        this.environment = environment;
        if (overrideCreatedAt != null) {
            this.createdAt = overrideCreatedAt;
        } else {
            this.createdAt = ZonedDateTime.now();
        }
    }
}
