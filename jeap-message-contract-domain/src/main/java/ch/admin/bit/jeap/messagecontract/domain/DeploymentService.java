package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.persistence.DeploymentRepository;
import ch.admin.bit.jeap.messagecontract.persistence.MessageContractRepository;
import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentService {

    private final MessageContractRepository messageContractRepository;
    private final DeploymentRepository deploymentRepository;

    @Transactional(readOnly = true)
    public List<Deployment> findLast10Deployments() {
        return deploymentRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Deployment> findNewestDeploymentPerAppAndEnv() {
        return deploymentRepository.findNewestDeploymentPerAppAndEnv();
    }

    @Transactional
    public Deployment saveNewDeployment(String appName, String appVersion, String environment) {
        if (!messageContractRepository.existsByAppNameAndAppVersion(appName, appVersion)) {
            log.info("Application with name '{}' not found in the db. Deployment ignored and not saved", appName);
            return null;
        } else {
            Deployment deployment = Deployment.builder()
                    .appName(appName)
                    .appVersion(appVersion)
                    .environment(environment)
                    .build();
            return deploymentRepository.save(deployment);
        }
    }

    @Transactional
    public void deleteDeployment(String appName, String environment) {
        deploymentRepository.deleteDeployment(appName, environment);
    }
}
