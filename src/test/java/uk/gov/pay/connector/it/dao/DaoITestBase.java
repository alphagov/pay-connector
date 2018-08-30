package uk.gov.pay.connector.it.dao;

import com.google.inject.persist.jpa.JpaPersistModule;
import com.spotify.docker.client.exceptions.DockerException;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.rules.PostgresDockerRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DaoITestBase {
    @Rule
    public PostgresDockerRule postgres;

    protected DatabaseTestHelper databaseTestHelper;
    private JpaPersistModule jpaModule;
    protected GuicedTestEnvironment env;

    public DaoITestBase() {
        try {
            postgres = new PostgresDockerRule();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup() throws Exception {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", postgres.getUsername());
        properties.put("javax.persistence.jdbc.password", postgres.getPassword());

        jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        databaseTestHelper = new DatabaseTestHelper(new DBI(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword()));

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword());

            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        } finally {
            if(connection != null)
                connection.close();
        }

        env = GuicedTestEnvironment.from(jpaModule).start();
    }

    @After
    public void tearDown() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword());
            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.dropAll();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        env.stop();
    }

}
