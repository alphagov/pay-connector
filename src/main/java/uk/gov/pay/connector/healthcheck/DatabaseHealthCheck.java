package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseHealthCheck extends HealthCheck {

    private ConnectorConfiguration configuration;

    @Inject
    public DatabaseHealthCheck(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Result check() throws Exception {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    configuration.getDataSourceFactory().getUrl(),
                    configuration.getDataSourceFactory().getUser(),
                    configuration.getDataSourceFactory().getPassword());
            connection.createStatement().execute("SELECT '1'");
        } catch (Exception e) {
            Result.unhealthy(e.getMessage());
        } finally {
            connection.close();
        }
        return Result.healthy();
    }
}
