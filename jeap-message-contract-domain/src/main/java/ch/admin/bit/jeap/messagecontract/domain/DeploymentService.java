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

    private final DeploymentRepository deploymentRepository;
    private final MessageContractRepository messageContractRepository;

    @Transactional(readOnly = true)
    public List<Deployment> findLast10Deployments() {
        return deploymentRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Deployment> findNewestDeploymentPerAppAndEnv() {
        return deploymentRepository.findNewestDeploymentPerAppAndEnv();
    }

    /**
     * Saves a deployment of an application version on an environment.
     * <p>
     * Every deployment is saved, including an application version without message contracts: the contracts of the
     * previously deployed version must not stay reported as deployed. As a deployed application version without
     * contracts also means that no compatibility is checked against this application anymore, it is logged for
     * applications that have contracts for other versions, which points at a contract publication that did not
     * happen or at an application version registered differently from the one the contracts were uploaded for.
     */
    @Transactional
    public Deployment saveNewDeployment(String appName, String appVersion, String environment) {
        logIfDeployedVersionHasNoContracts(appName, appVersion, environment);
        Deployment deployment = Deployment.builder()
                .appName(appName)
                .appVersion(appVersion)
                .environment(environment)
                .build();
        return deploymentRepository.save(deployment);
    }

    private void logIfDeployedVersionHasNoContracts(String appName, String appVersion, String environment) {
        if (messageContractRepository.existsByAppNameAndAppVersion(appName, appVersion)) {
            return;
        }
        if (messageContractRepository.existsByAppName(appName)) {
            log.warn("No message contracts found for app={}:{} deployed on environment={}, while other versions of " +
                            "this application have contracts. No contracts are reported as deployed for this " +
                            "application until contracts are uploaded for the deployed application version.",
                    appName, appVersion, environment);
        } else {
            log.info("No message contracts found for app={}:{} deployed on environment={}", appName, appVersion, environment);
        }
    }

    @Transactional
    public void deleteDeployment(String appName, String environment) {
        deploymentRepository.deleteDeployment(appName, environment);
    }
}
