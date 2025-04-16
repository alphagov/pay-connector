package uk.gov.pay.connector.pact.util;

import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Collections;

public class GatewayAccountUtil {

    public static void setUpGatewayAccount(DatabaseTestHelper dbHelper, long accountId) {
        setUpGatewayAccount(dbHelper, accountId, "a-valid-external-service-id");
    }
    
    public static void setUpGatewayAccount(DatabaseTestHelper dbHelper, long accountId, String serviceExternalId) {
        if (dbHelper.getGatewayAccount(accountId) == null) {
            DatabaseFixtures
                    .withDatabaseTestHelper(dbHelper)
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withPaymentProvider("sandbox")
                    .withDescription("aDescription")
                    .withAnalyticsId("8b02c7e542e74423aa9e6d0f0628fd58")
                    .withServiceName("a cool service")
                    .withServiceId(serviceExternalId)
                    .withCardTypeEntities(Collections.singletonList(dbHelper.getVisaDebitCard()))
                    .insert();
        } else {
            dbHelper.deleteAllChargesOnAccount(accountId);
        }
    }

}
