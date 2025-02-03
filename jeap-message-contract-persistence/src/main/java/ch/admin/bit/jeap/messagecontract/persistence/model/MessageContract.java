package ch.admin.bit.jeap.messagecontract.persistence.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings("findbugs:UWF_NULL_FIELD") // deletedAt is set via DB query
public class MessageContract {
    @EqualsAndHashCode.Include
    @Id
    private UUID id;
    @NonNull
    private String appName;
    @NonNull
    private String appVersion;
    @NonNull
    private String messageType;
    @NonNull
    private String messageTypeVersion;
    @NonNull
    private String topic;
    @Enumerated(EnumType.STRING)
    @NonNull
    private MessageContractRole role;
    @NonNull
    private String registryUrl;
    private String commitHash;
    private String branch;
    @Enumerated(EnumType.STRING)
    @NonNull
    private CompatibilityMode compatibilityMode;
    @Setter
    private String avroProtocolSchema;
    private ZonedDateTime createdAt;
    private boolean deleted;
    private ZonedDateTime deletedAt;
    private String encryptionKeyId;
    private String transactionId;

    @Builder
    @SuppressWarnings("java:S107")
    private MessageContract(String appName,
                            String appVersion,
                            String messageType,
                            String messageTypeVersion,
                            String topic,
                            MessageContractRole role,
                            String registryUrl,
                            String commitHash,
                            String branch,
                            String avroProtocolSchema,
                            CompatibilityMode compatibilityMode,
                            String encryptionKeyId,
                            String transactionId) {
        this.id = UUID.randomUUID();
        this.appName = appName;
        this.appVersion = appVersion;
        this.messageType = messageType;
        this.messageTypeVersion = messageTypeVersion;
        this.topic = topic;
        this.role = role;
        this.registryUrl = registryUrl;
        this.commitHash = commitHash;
        this.branch = branch;
        this.avroProtocolSchema = avroProtocolSchema;
        this.compatibilityMode = compatibilityMode;
        this.createdAt = ZonedDateTime.now();
        this.deleted = false;
        this.deletedAt = null;
        this.encryptionKeyId = encryptionKeyId;
        this.transactionId = transactionId;
    }

    public boolean referencesSpecificCommit() {
        return commitHash != null;
    }
}
