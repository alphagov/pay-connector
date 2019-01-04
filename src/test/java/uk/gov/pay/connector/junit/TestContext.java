package uk.gov.pay.connector.junit;

import com.google.inject.Injector;
import io.dropwizard.db.DataSourceFactory;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DatabaseTestHelper;

public class TestContext {

    private final ConnectorConfiguration connectorConfiguration;
    // This should be out of the text context really (since it is a specific class for this project)
    // but is fine for now
    private DatabaseTestHelper databaseTestHelper;
    private int port;
    private Injector injector;
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;

    TestContext(int port, ConnectorConfiguration connectorConfiguration, Injector injector) {
        this.injector = injector;
        DataSourceFactory dataSourceFactory = connectorConfiguration.getDataSourceFactory();
        databaseUrl = dataSourceFactory.getUrl();
        databaseUser = dataSourceFactory.getUser();
        databasePassword = dataSourceFactory.getPassword();
        DBI jdbi = new DBI(databaseUrl, databaseUser, databasePassword);
        this.databaseTestHelper = new DatabaseTestHelper(jdbi);
        this.port = port;
        this.connectorConfiguration = connectorConfiguration;
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public int getPort() {
        return port;
    }

    public <T> T getInstanceFromGuiceContainer(Class<T> clazz) {
        return injector.getInstance(clazz);
    }

    String getDatabaseUrl() {
        return databaseUrl;
    }

    String getDatabaseUser() {
        return databaseUser;
    }

    String getDatabasePassword() {
        return databasePassword;
    }
}
