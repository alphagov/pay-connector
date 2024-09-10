package uk.gov.pay.connector.rules;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;


import java.sql.Connection;
import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;
import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

public class PostgresTestDocker {

    private static final Logger logger = LoggerFactory.getLogger(PostgresTestDocker.class);

    private static final String DB_NAME = "connector_test";
    private static final String DB_USERNAME = "test";
    private static final String DB_PASSWORD = "test";
    private static PostgreSQLContainer<?> POSTGRES_CONTAINER;

    public static void getOrCreate() {
        try {
            if (POSTGRES_CONTAINER == null) {
                logger.info("Creating Postgres Container");

                POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:15.2")
                        .withUsername(DB_USERNAME)
                        .withPassword(DB_PASSWORD)
                        .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                                new HostConfig().withPortBindings(new PortBinding(
                                        Ports.Binding.bindIp("127.0.0.1"),
                                        new ExposedPort(POSTGRESQL_PORT)
                                ))
                        ));

                POSTGRES_CONTAINER.start();
                createDatabase();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getConnectionUrl() {
        return POSTGRES_CONTAINER.getJdbcUrl();
    }

    public static void stopContainer() {
        POSTGRES_CONTAINER.stop();
        POSTGRES_CONTAINER = null;
    }

    private static void createDatabase() {
        try (Connection connection = getConnection(getConnectionUrl(), DB_USERNAME, getDbPassword())) {
            connection.createStatement().execute("CREATE DATABASE " + DB_NAME + " WITH owner=" + DB_USERNAME + " TEMPLATE postgres");
            connection.createStatement().execute("GRANT ALL PRIVILEGES ON DATABASE " + DB_NAME + " TO " + DB_USERNAME);
            connection.createStatement().execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            connection.createStatement().execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getDbPassword() {
        return DB_PASSWORD;
    }

    public static String getDbUsername() {
        return DB_USERNAME;
    }

    public static String getDbDriverClass() {
        return POSTGRES_CONTAINER.getDriverClassName();
    }

    public static PostgreSQLContainer getPostgresContainer() {
        return POSTGRES_CONTAINER;
    }
}
