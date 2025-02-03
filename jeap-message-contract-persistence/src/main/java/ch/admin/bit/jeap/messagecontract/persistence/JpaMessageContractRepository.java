package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContract;
import ch.admin.bit.jeap.messagecontract.persistence.model.MessageContractRole;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JpaMessageContractRepository extends CrudRepository<MessageContract, UUID> {

    List<MessageContractInfo> findByDeletedFalse();

    @Modifying
    @Query("""
            update MessageContract set deleted=true, deletedAt=:deletedAt \
            where appName=:appName and appVersion=:appVersion and messageType=:messageType and \
            messageTypeVersion=:messageTypeVersion and topic=:topic and role=:role\
            """)
    int deleteContract(@Param("deletedAt") ZonedDateTime deletedAt,
                       @Param("appName") String appName,
                       @Param("appVersion") String appVersion,
                       @Param("messageType") String messageType,
                       @Param("messageTypeVersion") String messageTypeVersion,
                       @Param("topic") String topic,
                       @Param("role") MessageContractRole role);

    /**
     * Note: delete using derived query in spring does not work correctly with insert in same hibernate session
     * (see https://stackoverflow.com/questions/50370376/spring-data-deleteall-and-insert-in-same-transaction)
     */
    @Modifying(flushAutomatically = true)
    @Query("delete MessageContract where id=:id")
    void deleteContractById(@Param("id") UUID id);

    /**
     * Note: delete using derived query in spring does not work correctly with insert in same hibernate session
     * (see https://stackoverflow.com/questions/50370376/spring-data-deleteall-and-insert-in-same-transaction)
     */
    @Modifying(flushAutomatically = true)
    @Query("delete MessageContract where appName=:appName and appVersion=:appVersion")
    int deleteByAppNameAndAppVersion(@Param("appName") String appName, @Param("appVersion") String appVersion);

    /**
     * Note: delete using derived query in spring does not work correctly with insert in same hibernate session
     * (see https://stackoverflow.com/questions/50370376/spring-data-deleteall-and-insert-in-same-transaction)
     */
    @Modifying(flushAutomatically = true)
    @Query("delete MessageContract where appName=:appName and appVersion=:appVersion and (transactionId is null or transactionId<>:transactionId)")
    int deleteByAppNameAndAppVersionNotSameTransactionId(@Param("appName") String appName, @Param("appVersion") String appVersion, @Param("transactionId") String transactionId);


    boolean existsByAppNameAndAppVersionAndDeletedFalse(String appName, String appVersion);

    List<MessageContract> findAllByAppNameAndAppVersionAndDeletedFalse(String appName, String appVersion);

    List<MessageContract> findAllByAppNameAndAppVersionAndTransactionIdAndDeletedFalse(String appName, String appVersion, String transactionId);

    @Query("""
            select distinct appName from MessageContract \
            where topic = :topic and messageType = :messageType and role = :role and deleted = false\
            """)
    Set<String> distinctAppNameByRoleForMessageTypeOnTopic(@Param("messageType") String messageType,
                                                           @Param("topic") String topic,
                                                           @Param("role") MessageContractRole role);

    List<MessageContract> findAllByAppNameAndAppVersionAndMessageTypeAndTopicAndRoleAndDeletedFalse(String appName, String deployedAppVersion, String messageType, String topic, MessageContractRole role);

    @Query(value = """
            SELECT DISTINCT mc.app_name AS appName, mc.app_version AS appVersion, 
                            mc.message_type AS messageType, mc.message_type_version AS messageTypeVersion,
                            mc.topic, mc.role, 
                            mc.registry_url AS registryUrl, mc.commit_hash AS commitHash, mc.branch,
                            mc.compatibility_mode AS compatibilityMode, 
                            mc.encryption_key_id AS encryptionKeyId
            FROM message_contract mc
                INNER JOIN (
                    SELECT DISTINCT ON (d.app_name) d.app_name, d.app_version, d.created_at
                    FROM deployment d
                    WHERE d.environment = :environment
                    ORDER BY d.app_name, d.created_at DESC
                ) as latest_deployment 
                ON latest_deployment.app_name = mc.app_name AND latest_deployment.app_version = mc.app_version 
            WHERE mc.deleted = false;
            """, nativeQuery = true)
    List<MessageContractInfo> findAllByEnvironment(@Param("environment") String environment);

    List<MessageContractInfo> findAllByDeletedFalse();
}
