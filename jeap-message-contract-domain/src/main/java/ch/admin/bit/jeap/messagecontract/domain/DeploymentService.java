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
        return saveNewDeployment(appName, appVersion, environment, false);
    }

    /**
     * Saves a deployment of an application version on an environment.
     * <p>
     * An application version without contracts is only known to this service if the caller explicitly declares that
     * the deployed application version has no message contracts. Without that declaration the absence of contracts
     * cannot be distinguished from a failed contract publication, and the deployment is ignored.
     *
     * @param noMessageContracts the caller declares that the deployed application version has no message contracts
     * @return the saved deployment, or null if the deployment has been ignored
     */
    @Transactional
    public Deployment saveNewDeployment(String appName, String appVersion, String environment, boolean noMessageContracts) {
        boolean hasContracts = messageContractRepository.existsByAppNameAndAppVersion(appName, appVersion);
        if (noMessageContracts) {
            if (hasContracts) {
                throw new IllegalArgumentException("Application '%s' version '%s' has message contracts, it must not be deployed as an application version without message contracts"
                        .formatted(appName, appVersion));
            }
            log.info("Saving deployment of app={}:{} on environment={} declared to have no message contracts", appName, appVersion, environment);
        } else if (!hasContracts) {
            log.info("Application with name '{}' not found in the db. Deployment ignored and not saved", appName);
            return null;
        }
        Deployment deployment = Deployment.builder()
                .appName(appName)
                .appVersion(appVersion)
                .environment(environment)
                .build();
        return deploymentRepository.save(deployment);
    }

    @Transactional
    public void deleteDeployment(String appName, String environment) {
        deploymentRepository.deleteDeployment(appName, environment);
    }
}
