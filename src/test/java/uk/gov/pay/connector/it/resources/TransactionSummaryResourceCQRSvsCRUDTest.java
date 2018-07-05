package uk.gov.pay.connector.it.resources;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class TransactionSummaryResourceCQRSvsCRUDTest {

    private static final long GATEWAY_ACCOUNT_ID = 4815162343L;
    
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private RestAssuredClient connectorApi = new RestAssuredClient(app, Long.toString(GATEWAY_ACCOUNT_ID));
    
    @Test
    public void cqrsVsCrudTest() {
        //given a thousand charge events and 500 refunds
        
        System.out.println("Adding 1000 charge events and 500 refunds...\n");
        
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(GATEWAY_ACCOUNT_ID)
                .insert();
        String url = app.getConf().getDataSourceFactory().getUrl();
        for (int i = 0; i < 10000; i++) {
            ZonedDateTime dateTime = ZonedDateTime.now();
            
            DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestCharge()
                    .withChargeStatus(CAPTURED)
                    .withCreatedDate(dateTime)
                    .withAmount(200_00L)
                    .withTestAccount(testAccount)
                    .insert();

            DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aSuccessfulChargeEvent()
                    .withAccountGatewayId(testAccount.getAccountId())
                    .withCreatedDate(dateTime)
                    .withExternalChargeId(testCharge.getExternalChargeId())
                    .withAmount(200_00L)
                    .insert();
            
            if (i%2 == 0) {
                DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(testCharge)
                        .withRefundStatus(REFUNDED)
                        .withCreatedDate(dateTime.plusHours(1))
                        .withAmount(10_00L)
                        .insert();

                DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aRefundedEvent()
                        .withAccountGatewayId(testAccount.getAccountId())
                        .withCreatedDate(dateTime)
                        .withExternalChargeId(testCharge.getExternalChargeId())
                        .withAmount(200_00L)
                        .insert();
            }
        }
        
        System.out.println("Added\n");
        
        String fromDate = ZonedDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_INSTANT);
        String toDate = ZonedDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_INSTANT);
        
        //get them by cqrs
        long startTime = System.currentTimeMillis();
        connectorApi
                .withQueryParam("from_date", fromDate)
                .withQueryParam("to_date", toDate)
                .withQueryParam("accountId", String.valueOf(testAccount.getAccountId()))
                .getTransactionsSummaryByCqrs()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("successful_payments.count", is(10000));

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("\nTime to run report by CQRS: " + Duration.ofMillis(duration).toString());
        
        //get them by crud
        startTime = System.currentTimeMillis();
        connectorApi
                .withQueryParam("from_date", fromDate)
                .withQueryParam("to_date", toDate)
                .withQueryParam("accountId", String.valueOf(testAccount.getAccountId()))
                .getTransactionsSummary()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("successful_payments.count", is(10000));

        endTime = System.currentTimeMillis();
        duration = endTime - startTime;
        System.out.println("\nTime to run report by CRUD: " + Duration.ofMillis(duration).toString());
        
    }
    
}
