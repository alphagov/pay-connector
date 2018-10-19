package uk.gov.pay.connector.junit;

import com.google.inject.Injector;
import io.dropwizard.db.DataSourceFactory;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.util.DatabaseTestHelper;

public class TestContext {

    private final ConnectorConfiguration connectorConfiguration;
    // This should be out of the text context really (since it is a specific class for this project)
    // but is fine for now
    private DatabaseTestHelper databaseTestHelper;
    private int port;
    private Injector injector;

    TestContext(int port, ConnectorConfiguration connectorConfiguration, Injector injector) {
        this.injector = injector;
        DataSourceFactory dataSourceFactory = connectorConfiguration.getDataSourceFactory();
        DBI jdbi = new DBI(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword());
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

    public ExecutorServiceConfig getExecutorServiceConfig() {
        return connectorConfiguration.getExecutorServiceConfig();
    }

    public <T> T getInstanceFromGuiceContainer(Class<T> clazz) {
        return injector.getInstance(clazz);
    }
}
