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
     * A deployment of an application version without contracts is ignored, as the absence of contracts cannot be
     * distinguished from a failed contract publication. The caller can declare that the deployed application version
     * has no message contracts, in which case the deployment is saved for an application known to this service. The
     * declaration is ignored if the application version does have contracts: those contracts belong to the deployed
     * version and are reported as deployed.
     *
     * @param noMessageContracts the caller declares that the deployed application version has no message contracts
     * @return the saved deployment, or null if the deployment has been ignored
     */
    @Transactional
    public Deployment saveNewDeployment(String appName, String appVersion, String environment, boolean noMessageContracts) {
        if (messageContractRepository.existsByAppNameAndAppVersion(appName, appVersion)) {
            if (noMessageContracts) {
                log.warn("Deployment of app={}:{} on environment={} has been declared to have no message contracts, " +
                                "but contracts are registered for this application version. Saving the deployment with its contracts.",
                        appName, appVersion, environment);
            }
            return save(appName, appVersion, environment);
        }

        if (noMessageContracts && messageContractRepository.existsByAppName(appName)) {
            log.info("Saving deployment of app={}:{} on environment={} declared to have no message contracts",
                    appName, appVersion, environment);
            return save(appName, appVersion, environment);
        }

        log.info("Application with name '{}' not found in the db. Deployment ignored and not saved", appName);
        return null;
    }

    private Deployment save(String appName, String appVersion, String environment) {
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
