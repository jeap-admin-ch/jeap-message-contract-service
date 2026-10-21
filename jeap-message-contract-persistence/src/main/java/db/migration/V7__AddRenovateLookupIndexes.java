package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Indexes for the Renovate datasource lookup of the currently deployed contracts of a message type.
 * <p>
 * The lookup filters non-deleted contracts by the lower-cased message type and joins each application's latest
 * deployment in an environment. Without these indexes PostgreSQL resorts to sequential scans of both tables for
 * every request, which overloads the database when Renovate queries many message types at once.
 * <p>
 * This is a Java migration because the expression index with a partial predicate is PostgreSQL syntax that the H2
 * database used by tests does not support. The tables are small, so a plain (non-concurrent) index build within
 * the migration transaction is sufficient.
 */
@SuppressWarnings("java:S101") // Flyway derives the version and description from the class name
public class V7__AddRenovateLookupIndexes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            if (isPostgreSql(connection)) {
                statement.execute("CREATE INDEX message_contract_lower_message_type_not_deleted " +
                        "ON message_contract (lower(message_type)) WHERE deleted = false");
            }
            statement.execute("CREATE INDEX deployment_app_name_environment_created_at " +
                    "ON deployment (app_name, environment, created_at DESC)");
        }
    }

    private static boolean isPostgreSql(Connection connection) throws SQLException {
        return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }
}
