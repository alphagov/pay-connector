package uk.gov.pay.connector.managed;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.util.DependentResource;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;

public class DependentResourceChecker implements Managed {

    public static final int PROGRESSIVE_SECONDS_TO_WAIT = 5;
    private static final Logger logger = LoggerFactory.getLogger(DependentResourceChecker.class);

    private final DependentResource dependentResource;

    @Inject
    public DependentResourceChecker(DependentResource dependentResource) {
        this.dependentResource = dependentResource;
    }

    @Override
    public void start() throws Exception {
        waitingForDatabaseConnectivity();
    }

    @Override
    public void stop() throws Exception {}

    private void waitingForDatabaseConnectivity() {
        logger.info("Checking for database availability >>>");
        boolean databaseAvailable = isDatabaseAvailable();

        long timeToWait = 0;
        while(!databaseAvailable) {
            timeToWait += PROGRESSIVE_SECONDS_TO_WAIT;
            logger.info("Waiting for "+ timeToWait +" seconds till the database is available ...");
            dependentResource.sleep(timeToWait * 1000);
            databaseAvailable = isDatabaseAvailable();
        }
        logger.info("Database available.");
    }


    public boolean isDatabaseAvailable() {
        try {
            Connection connection = dependentResource.getDatabaseConnection();
            connection.close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
