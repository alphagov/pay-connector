package uk.gov.pay.connector.managed;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.util.DependentResource;

import javax.inject.Inject;
import java.sql.SQLException;

public class DependentResourceChecker implements Managed {

    public static final int INITIALI_SECONDS_TO_WAIT = 5;
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

    private void waitingForDatabaseConnectivity() {
        logger.info("Checking for database availability");
        boolean databaseAvailable = isDatabaseAvailable();

        while(!databaseAvailable) {
            logger.info("Waiting for "+ INITIALI_SECONDS_TO_WAIT +" seconds till the database is available ...");
            dependentResource.sleep(INITIALI_SECONDS_TO_WAIT * 1000);
            databaseAvailable = isDatabaseAvailable();
        }
        logger.info("Database available");
    }

    @Override
    public void stop() throws Exception {

    }

    public boolean isDatabaseAvailable() {
        try {
            dependentResource.getDatabaseConnection();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
