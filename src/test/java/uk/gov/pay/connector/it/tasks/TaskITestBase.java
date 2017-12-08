package uk.gov.pay.connector.it.tasks;

import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.db.DataSourceFactory;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.it.dao.GuicedTestEnvironment;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class TaskITestBase {
    private static final String CONNECTOR_UNIT = "ConnectorUnit";
    private static final String MIGRATIONS_XML = "migrations.xml";
    @Rule
    public DropwizardAppWithPostgresRule dropwizardRule;

    protected GuicedTestEnvironment env;
    private DataSourceFactory dataSourceFactory;
    protected DatabaseTestHelper databaseTestHelper;

    public TaskITestBase() {
        dropwizardRule = new DropwizardAppWithPostgresRule();
    }

    @Before
    public void setup() throws Exception {
        final Properties properties = new Properties();
        dataSourceFactory = dropwizardRule.getConf().getDataSourceFactory();
        properties.put("javax.persistence.jdbc.driver", dataSourceFactory.getDriverClass());
        properties.put("javax.persistence.jdbc.url", dataSourceFactory.getUrl());
        properties.put("javax.persistence.jdbc.user", dataSourceFactory.getUser());
        properties.put("javax.persistence.jdbc.password", dataSourceFactory.getPassword());
        properties.put("eclipselink.cache.shared.default", dropwizardRule.getConf().getJpaConfiguration().getCacheSharedDefault());

        databaseTestHelper = new DatabaseTestHelper(
                new DBI(dataSourceFactory.getUrl(),
                        dataSourceFactory.getUser(),
                        dataSourceFactory.getPassword())
        );

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    dataSourceFactory.getUrl(),
                    dataSourceFactory.getUser(),
                    dataSourceFactory.getPassword());

            Liquibase migrator = new Liquibase(
                    MIGRATIONS_XML,
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection)
            );
            migrator.update("");
        } finally {
            if (connection != null)
                connection.close();
        }
        final JpaPersistModule jpaModule = new JpaPersistModule(CONNECTOR_UNIT);
        jpaModule.properties(properties);
        env = GuicedTestEnvironment.from(jpaModule).start();
    }

    @After
    public void tearDown() {
        Connection connection;
        try {
            connection = DriverManager.getConnection(
                    dataSourceFactory.getUrl(),
                    dataSourceFactory.getUser(),
                    dataSourceFactory.getPassword());
            Liquibase migrator = new Liquibase(
                    MIGRATIONS_XML,
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection)
            );
            migrator.dropAll();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        env.stop();
    }

}
