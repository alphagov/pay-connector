package uk.gov.pay.connector.junit;

import com.spotify.docker.client.DefaultDockerClient;

import java.sql.Connection;
import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;
import static uk.gov.pay.connector.junit.PostgresTestContainer.DB_PASSWORD;
import static uk.gov.pay.connector.junit.PostgresTestContainer.DB_USERNAME;

final class PostgresTestDocker {

    private static final String DB_NAME = "connector_tests";
    private static PostgresTestContainer container;

    static void getOrCreate(String image) {
        try {
            if (container == null) {
                container = new PostgresTestContainer(DefaultDockerClient.fromEnv().build(), image);
                createDatabase();
            }
        } catch (Exception e) {
            throw new PostgresTestDockerException(e);
        }
    }
    
    private static void createDatabase() {
        try (Connection connection = getConnection(container.getPostgresDbUri(), DB_USERNAME, DB_PASSWORD)) {
            connection.createStatement().execute("CREATE DATABASE " + DB_NAME + " WITH owner=" + DB_USERNAME + " TEMPLATE postgres");
            connection.createStatement().execute("GRANT ALL PRIVILEGES ON DATABASE " + DB_NAME + " TO " + DB_USERNAME);
        } catch (SQLException e) {
            throw new PostgresTestDockerException(e);
        }
    }

    static String getDbRootUri() {
        return container.getPostgresDbUri();
    }

    static String getDbUri() {
        return getDbRootUri() + DB_NAME;
    }
}
