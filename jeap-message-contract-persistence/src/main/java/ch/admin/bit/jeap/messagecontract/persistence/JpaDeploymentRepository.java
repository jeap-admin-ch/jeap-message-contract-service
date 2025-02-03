package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaDeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findTop10ByOrderByCreatedAtDesc();

    @Query("select d.appVersion from Deployment d where d.appName = :appName and d.environment = :environment order by d.createdAt desc")
    List<String> findAppVersionCurrentlyDeployedOnEnvironment(
            @Param("appName") String appName, @Param("environment") String environment, Pageable pageable);


    @Query(value = """
            SELECT DISTINCT ON (app_name, environment)
            id, app_name, app_version, environment, created_at
            FROM deployment
            ORDER BY app_name, environment, created_at DESC
            """, nativeQuery = true)
    List<Deployment> findNewestDeploymentPerAppAndEnv();

    void deleteByAppNameAndEnvironment(String appName, String environment);
}
