package ch.admin.bit.jeap.messagecontract.web.api;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the tests against PostgreSQL, the database used in production.
 * <p>
 * The container is started once and shared by all test classes of the test run instead of being started and stopped
 * per test class: the Spring test contexts are cached across test classes, and a cached context must not be left with
 * a stopped database. Testcontainers removes the container when the test run ends.
 */
abstract class PostgresTestContainerBase {

    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine")
                    .asCompatibleSubstituteFor("postgres:17-alpine"));

    static {
        postgres.start();
    }
}
