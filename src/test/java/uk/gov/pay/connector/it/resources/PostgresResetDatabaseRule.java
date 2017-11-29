package uk.gov.pay.connector.it.resources;

import io.dropwizard.db.DataSourceFactory;
import org.apache.commons.lang3.StringUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.sql.Connection;
import java.sql.DriverManager;

public class PostgresResetDatabaseRule implements TestRule {

    private final DropwizardAppWithPostgresRule app;

    public PostgresResetDatabaseRule(DropwizardAppWithPostgresRule app) {
        this.app = app;
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                //drop database and recreate
                Connection connection = null;
                DataSourceFactory dataSourceFactory = app.getConf().getDataSourceFactory();
                String url = StringUtils.removeEnd(dataSourceFactory.getUrl(), "connectorintegrationtests");
                try {
                    connection = DriverManager.getConnection(url, dataSourceFactory.getUser(), dataSourceFactory.getPassword());
                    connection.createStatement().execute("SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity"
                            + " WHERE pg_stat_activity.datname = 'connectorintegrationtests'");
                    connection.createStatement().execute("DROP DATABASE connectorintegrationtests");
                    connection.createStatement().execute("CREATE DATABASE connectorintegrationtests WITH TEMPLATE templatedb OWNER postgres");
                } finally {
                    if (connection != null)
                        connection.close();
                }
            }
        };
    }
}
