package ch.admin.bit.jeap.messagecontract.domain;

import ch.admin.bit.jeap.messagecontract.persistence.model.Deployment;

/**
 * The outcome of a deployment registration.
 *
 * @param deployment the saved deployment, or null if the deployment has been ignored
 */
public record DeploymentRegistration(Deployment deployment, Outcome outcome) {

    public enum Outcome {
        /**
         * The deployment has been registered.
         */
        REGISTERED,
        /**
         * The deployment has been registered, but the declaration that the application version has no message
         * contracts was ignored, as contracts are registered for the deployed application version.
         */
        REGISTERED_DECLARATION_IGNORED,
        /**
         * The deployment has been ignored: the deployed application version has no message contracts and the caller
         * did not declare that, or the application is unknown to this service.
         */
        IGNORED
    }

    static DeploymentRegistration registered(Deployment deployment) {
        return new DeploymentRegistration(deployment, Outcome.REGISTERED);
    }

    static DeploymentRegistration declarationIgnored(Deployment deployment) {
        return new DeploymentRegistration(deployment, Outcome.REGISTERED_DECLARATION_IGNORED);
    }

    static DeploymentRegistration ignored() {
        return new DeploymentRegistration(null, Outcome.IGNORED);
    }
}
