package ch.admin.bit.jeap.messagecontract.persistence;

public interface MessageContractInfo {

    String getAppName();

    String getAppVersion();

    String getMessageType();

    String getMessageTypeVersion();

    String getTopic();

    String getRole();

    String getRegistryUrl();

    String getCommitHash();

    String getBranch();

    String getCompatibilityMode();

    String getEncryptionKeyId();
}
