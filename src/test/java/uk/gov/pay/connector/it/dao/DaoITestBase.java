package uk.gov.pay.connector.it.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.spotify.docker.client.exceptions.DockerException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jdbi.v3.core.Jdbi;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.testing.db.PostgresDockerRule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

abstract public class DaoITestBase {

    @ClassRule
    public static PostgresDockerRule postgres;

    protected static DatabaseTestHelper databaseTestHelper;
    protected static GuicedTestEnvironment env;
    protected static ObjectMapper objectMapper = new ObjectMapper();
    
    private static String CHANGE_LOG_FILE = "it-migrations.xml";

    static {
        postgres = new PostgresDockerRule("15.2");
    }

    @BeforeClass
    public static void setup() throws Exception {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", postgres.getPassword());

        properties.put("eclipselink.logging.level", "WARNING");
        properties.put("eclipselink.logging.level.sql", "WARNING");
        properties.put("eclipselink.query-results-cache", "false");
        properties.put("eclipselink.cache.shared.default", "false");
        properties.put("eclipselink.ddl-generation.output-mode", "database");

        JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        databaseTestHelper = new DatabaseTestHelper(Jdbi.create(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword()));

        try (Connection connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase migrator = new Liquibase(CHANGE_LOG_FILE, new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        }

        env = GuicedTestEnvironment.from(jpaModule).start();
    }

    @AfterClass
    public static void tearDown() {
        try {
            Connection connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword());
            Liquibase migrator = new Liquibase(CHANGE_LOG_FILE, new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.dropAll();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        env.stop();
    }

}
