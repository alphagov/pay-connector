package uk.gov.pay.connector.it.dao;

import com.google.inject.persist.jpa.JpaPersistModule;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

abstract public class DaoITestBase {

//    @ClassRule
//    public static PostgresDockerRule postgres;
    
    public static EmbeddedPostgres embeddedPostgres;

    protected static DatabaseTestHelper databaseTestHelper;
    protected static GuicedTestEnvironment env;

    static {
        try {
//            postgres = new PostgresDockerRule();
            embeddedPostgres = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        final Properties properties = new Properties();
        
        
//        properties.put("javax.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("javax.persistence.jdbc.url", embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        properties.put("javax.persistence.jdbc.user", "postgres");
        properties.put("javax.persistence.jdbc.password", "postgres");

        properties.put("eclipselink.logging.level", "WARNING");
        properties.put("eclipselink.logging.level.sql", "WARNING");
        properties.put("eclipselink.query-results-cache", "false");
        properties.put("eclipselink.cache.shared.default", "false");
        properties.put("eclipselink.ddl-generation.output-mode", "database");

        JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        databaseTestHelper = new DatabaseTestHelper(new DBI(embeddedPostgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres"));

        try (Connection connection = embeddedPostgres.getPostgresDatabase().getConnection()) {
            Statement s = connection.createStatement();
            s.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            s.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA pg_catalog");
            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        }

        env = GuicedTestEnvironment.from(jpaModule).start();
    }

    @AfterClass
    public static void tearDown() {
//        Connection connection;
        try {
            Connection connection = embeddedPostgres.getPostgresDatabase().getConnection();
            Liquibase migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.dropAll();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        env.stop();
    }

}
