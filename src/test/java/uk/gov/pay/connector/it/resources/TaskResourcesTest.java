package uk.gov.pay.connector.it.resources;

import org.junit.Test;

public class TaskResourcesTest extends GatewayAccountResourceTestBase {

    @Test
    public void checkTaskResourceMigrateChargeEvents_isHealthy() {
        String PATH = "/tasks/migrate-charge-events-to-charge-transaction-events";
        givenAdminSetup()
                .post(PATH)
                .then()
                .statusCode(200);
    }
}
