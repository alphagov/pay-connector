package uk.gov.pay.connector.it.dao;

import com.google.inject.persist.jpa.JpaPersistModule;
import com.spotify.docker.client.exceptions.DockerException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.rules.PostgresDockerRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

abstract public class DaoITestBase {

    @ClassRule
    public static PostgresDockerRule postgres;

    protected static DatabaseTestHelper databaseTestHelper;
    protected static GuicedTestEnvironment env;

    static {
        try {
            postgres = new PostgresDockerRule();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", postgres.getPassword());
        properties.put("eclipselink.session.customizer", "uk.gov.pay.connector.util.ConnectorSessionCustomiser");

        JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        databaseTestHelper = new DatabaseTestHelper(new DBI(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword()));

        try (Connection connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        }

        env = GuicedTestEnvironment.from(jpaModule).start();
    }

    @AfterClass
    public static void tearDown() {
//        Connection connection;
        try {
            Connection connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword());
            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.dropAll();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        env.stop();
    }

}
