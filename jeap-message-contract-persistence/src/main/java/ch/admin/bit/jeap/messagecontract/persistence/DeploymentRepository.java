package ch.admin.bit.jeap.messagecontract.persistence;

import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeploymentRepository {

    private final JpaDeploymentRepository jpaRepository;

    public List<Deployment> findTop10ByOrderByCreatedAtDesc() {
        return jpaRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public Deployment save(Deployment deployment) {
        return jpaRepository.save(deployment);
    }

    public Optional<String> findAppVersionCurrentlyDeployedOnEnvironment(String appName, String environment) {
        return jpaRepository.findAppVersionCurrentlyDeployedOnEnvironment(appName, environment,
                        Pageable.ofSize(1))
                .stream().findFirst();
    }

    public List<Deployment> findNewestDeploymentPerAppAndEnv() {
        return jpaRepository.findNewestDeploymentPerAppAndEnv();
    }

    public void deleteDeployment(String appName, String environment) {
        jpaRepository.deleteByAppNameAndEnvironment(appName, environment);
    }

}
