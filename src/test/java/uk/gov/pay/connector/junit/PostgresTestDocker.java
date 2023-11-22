package uk.gov.pay.connector.junit;

import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import uk.gov.service.payments.commons.testing.db.PostgresContainer;

import java.sql.Connection;
import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;

final class PostgresTestDocker {
    private static PostgresContainer container;

    static void getOrCreate() {
        try {
            if (container == null) {
                container = new PostgresContainer("15.2");
                createDatabase();
            }
        } catch (Exception e) {
            throw new PostgresTestDockerException(e);
        }
    }
    
    private static void createDatabase() {
        final String dbUser = getDbUsername();
        
        try (Connection connection = getConnection(getDbUri(), dbUser, getDbPassword())) {
            Liquibase migrator = new Liquibase("it-migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        }  catch (LiquibaseException | SQLException e) {
            throw new PostgresTestDockerException(e);
        }
    }

    static String getDbUri() {
        return container.getConnectionUrl();
    }

    static String getDbPassword() {
        return container.getPassword();
    }

    static String getDbUsername() {
        return container.getUsername();
    }
}
