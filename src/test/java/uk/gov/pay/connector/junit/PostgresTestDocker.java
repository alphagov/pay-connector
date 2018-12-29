package uk.gov.pay.connector.junit;

import uk.gov.pay.commons.testing.db.PostgresContainer;

import java.sql.Connection;
import java.sql.SQLException;

import static java.lang.String.format;
import static java.sql.DriverManager.getConnection;

final class PostgresTestDocker {

    private static final String DB_NAME = "connector_tests";
    private static PostgresContainer container;

    static void getOrCreate() {
        try {
            if (container == null) {
                container = new PostgresContainer();
                createDatabase();
            }
        } catch (Exception e) {
            throw new PostgresTestDockerException(e);
        }
    }
    
    private static void createDatabase() {
        final String dbUser = getDbUsername();

        try (Connection connection = getConnection(getDbRootUri(), dbUser, getDbPassword())) {
            connection.createStatement().execute(format("CREATE DATABASE %s WITH owner=%s TEMPLATE postgres", DB_NAME, dbUser));
            connection.createStatement().execute(format("GRANT ALL PRIVILEGES ON DATABASE %s TO %s", DB_NAME, dbUser));
        } catch (SQLException e) {
            throw new PostgresTestDockerException(e);
        }
    }

    private static String getDbRootUri() {
        return container.getConnectionUrl();
    }

    static String getDbUri() {
        return getDbRootUri() + DB_NAME;
    }

    static String getDbPassword() {
        return container.getPassword();
    }

    static String getDbUsername() {
        return container.getUsername();
    }
}
