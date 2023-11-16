package uk.gov.pay.connector.junit;

import com.amazonaws.services.sqs.AmazonSQS;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.inject.Injector;
import io.dropwizard.db.DataSourceFactory;
import org.jdbi.v3.core.Jdbi;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.util.DatabaseTestHelper;

public class TestContext {

    private final ConnectorConfiguration connectorConfiguration;
    // This should be out of the text context really (since it is a specific class for this project)
    // but is fine for now
    private DatabaseTestHelper databaseTestHelper;
    private int port;
    private Injector injector;
    private WireMockServer wireMockServer;
    private AmazonSQS amazonSQS;
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;

    TestContext(int port, ConnectorConfiguration connectorConfiguration, Injector injector, 
                WireMockServer wireMockServer, AmazonSQS amazonSQS) {
        this.injector = injector;
        this.wireMockServer = wireMockServer;
        this.amazonSQS = amazonSQS;
        DataSourceFactory dataSourceFactory = connectorConfiguration.getDataSourceFactory();
        databaseUrl = dataSourceFactory.getUrl();
        databaseUser = dataSourceFactory.getUser();
        databasePassword = dataSourceFactory.getPassword();
        Jdbi jdbi = Jdbi.create(databaseUrl, databaseUser, databasePassword);
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

    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
    
    public AuthorisationConfig getAuthorisationConfig() {
        return connectorConfiguration.getAuthorisationConfig();
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
    
    public String getEventQueueUrl() {
        return connectorConfiguration.getSqsConfig().getEventQueueUrl();
    }

    public AmazonSQS getAmazonSQS() {
        return amazonSQS;
    }
}
